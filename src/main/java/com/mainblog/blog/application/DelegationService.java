package com.mainblog.blog.application;

import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogDelegation;
import com.mainblog.blog.domain.BlogDelegationRepository;
import com.mainblog.blog.domain.BlogMember;
import com.mainblog.blog.domain.BlogMemberRepository;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.blog.domain.BlogRole;
import com.mainblog.blog.domain.DelegationStatus;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그장 위임 (T055, BLG-08, FR-066~FR-072, research R-12).
 * 블로그당 대기 중 요청은 하나이고 요청 7일 뒤 자동 취소된다. 수락하면 이전 블로그장은 일반 멤버로 남는다.
 */
@Service
public class DelegationService {

    private final BlogDelegationRepository repository;
    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogPermissionService permissionService;
    private final NotificationWriter notifications;
    private final JdbcTemplate jdbc;
    private final BlogProperties properties;
    private final Clock clock;

    public DelegationService(BlogDelegationRepository repository, BlogRepository blogRepository,
                             BlogMemberRepository memberRepository, BlogPermissionService permissionService,
                             NotificationWriter notifications, JdbcTemplate jdbc, BlogProperties properties,
                             Clock clock) {
        this.repository = repository;
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.permissionService = permissionService;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /** 블로그장만, 그 블로그의 멤버·부블로그장 한 명에게. 폐쇄 예정이면 막는다(FR-071). */
    @Transactional
    public DelegationView request(long blogId, long userId, Long toUserId) {
        Blog blog = lock(blogId);
        permissionService.require(blog, userId, BlogPermission.OWNER);
        if (blog.isClosing()) {
            throw new BusinessException(ErrorCode.BLOG_CLOSING);
        }
        BlogMember target = toUserId == null ? null
                : memberRepository.findByBlogIdAndUserId(blogId, toUserId).orElse(null);
        if (target == null || target.isOwner()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (repository.findFirstByBlogIdAndStatus(blogId, DelegationStatus.PENDING).isPresent()) {
            throw new BusinessException(ErrorCode.DELEGATION_PENDING_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        BlogDelegation d = repository.save(BlogDelegation.request(blogId, userId, toUserId, now,
                now.plus(properties.delegation().expireAfter())));
        notifications.write(toUserId, NotificationType.DELEGATION_REQUESTED, "BLOG", blogId, userId,
                "'" + blog.getName() + "' 블로그의 블로그장 위임 요청을 받았습니다.", "DELEGATION_REQUESTED:" + d.getId());
        return view(d);
    }

    /** 블로그장 또는 받는 사람만 본다. 없거나 만료됐으면 empty(204). */
    @Transactional(readOnly = true)
    public Optional<DelegationView> pending(long blogId, long userId) {
        BlogContext ctx = permissionService.require(blogId, BlogPermission.MEMBER);
        Optional<BlogDelegation> d = repository.findFirstByBlogIdAndStatus(blogId, DelegationStatus.PENDING)
                .filter(x -> !x.isExpired(LocalDateTime.now(clock)));
        if (d.isPresent() && !ctx.member().isOwner() && !d.get().getToUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.BLOG_FORBIDDEN);
        }
        return d.map(this::view);
    }

    /** 받는 사람만. 이전 블로그장 행을 먼저 MEMBER로 바꿔 flush한 뒤 새 블로그장을 바꾼다(owner_blog_id 유니크, R-12). */
    @Transactional
    public DelegationView accept(long blogId, long userId) {
        Blog blog = lock(blogId);
        BlogDelegation d = myPending(blog, userId);
        LocalDateTime now = LocalDateTime.now(clock);
        memberRepository.findByBlogIdAndRole(blogId, BlogRole.OWNER).ifPresent(BlogMember::makeMember);
        memberRepository.flush();
        BlogMember next = memberRepository.findByBlogIdAndUserId(blogId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELEGATION_NOT_PENDING));
        next.makeOwner();
        memberRepository.flush();
        d.finish(DelegationStatus.ACCEPTED, now);
        notifyOwner(blog, d, NotificationType.DELEGATION_ACCEPTED, "수락");
        return view(d);
    }

    @Transactional
    public DelegationView reject(long blogId, long userId) {
        Blog blog = lock(blogId);
        BlogDelegation d = myPending(blog, userId);
        d.finish(DelegationStatus.REJECTED, LocalDateTime.now(clock));
        notifyOwner(blog, d, NotificationType.DELEGATION_REJECTED, "거절");
        return view(d);
    }

    /** 04:00 처리: 7일 지난 요청을 자동 취소하고 블로그장에게 알린다(FR-068). */
    @Transactional
    public int expireDue(LocalDateTime now) {
        List<BlogDelegation> due = repository.findAllByStatusAndExpiresAtLessThanEqual(DelegationStatus.PENDING, now);
        for (BlogDelegation d : due) {
            d.finish(DelegationStatus.AUTO_CANCELED, now);
            blogRepository.findById(d.getBlogId()).ifPresent(blog ->
                    notifyOwner(blog, d, NotificationType.DELEGATION_CANCELED, "응답 기한이 지나 자동 취소"));
        }
        return due.size();
    }

    /** 받을 사람이 블로그를 나가거나 강퇴되면 그 사람이 받은 요청을 취소한다(R-12 가정). 블로그 행을 잠근 트랜잭션에서 부른다. */
    public void cancelPendingFor(Blog blog, long toUserId) {
        repository.findFirstByBlogIdAndStatus(blog.getId(), DelegationStatus.PENDING)
                .filter(d -> d.getToUserId().equals(toUserId))
                .ifPresent(d -> {
                    d.finish(DelegationStatus.AUTO_CANCELED, LocalDateTime.now(clock));
                    notifyOwner(blog, d, NotificationType.DELEGATION_CANCELED, "받을 멤버가 블로그를 떠나 자동 취소");
                });
    }

    private BlogDelegation myPending(Blog blog, long userId) {
        permissionService.require(blog, userId, BlogPermission.MEMBER);
        BlogDelegation d = repository.findFirstByBlogIdAndStatus(blog.getId(), DelegationStatus.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELEGATION_NOT_PENDING));
        if (!d.getToUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.BLOG_FORBIDDEN);
        }
        if (d.isExpired(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.DELEGATION_NOT_PENDING);
        }
        return d;
    }

    private void notifyOwner(Blog blog, BlogDelegation d, NotificationType type, String what) {
        notifications.write(d.getFromUserId(), type, "BLOG", blog.getId(), null,
                "'" + blog.getName() + "' 블로그장 위임 요청이 " + what + "되었습니다.", type.name() + ":" + d.getId());
    }

    private Blog lock(long blogId) {
        return blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
    }

    private DelegationView view(BlogDelegation d) {
        return new DelegationView(d.getId(), d.getBlogId(), nickname(d.getFromUserId()), d.getToUserId(),
                nickname(d.getToUserId()), d.getStatus().name(), d.getRequestedAt(), d.getExpiresAt(),
                d.getRespondedAt());
    }

    private String nickname(long userId) {
        return jdbc.query("SELECT nickname FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, userId);
    }

    public record DelegationView(Long id, Long blogId, String fromNickname, Long toUserId, String toNickname,
                                 String status, LocalDateTime requestedAt, LocalDateTime expiresAt,
                                 LocalDateTime respondedAt) {}
}
