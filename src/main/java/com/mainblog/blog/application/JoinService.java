package com.mainblog.blog.application;

import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogJoinRequest;
import com.mainblog.blog.domain.BlogJoinRequestRepository;
import com.mainblog.blog.domain.BlogMember;
import com.mainblog.blog.domain.BlogMemberRepository;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.blog.domain.JoinPolicy;
import com.mainblog.blog.domain.JoinRequestStatus;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 참여 신청·승인·거절·취소 (T033, BLG-04, BLG-05, FR-017~FR-024, research R-08). */
@Service
public class JoinService {

    private static final List<JoinRequestStatus> REJECTED = List.of(JoinRequestStatus.REJECTED,
            JoinRequestStatus.AUTO_REJECTED);

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogJoinRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final BlogPermissionService permissionService;
    private final BlogLimitPolicy limitPolicy;
    private final BlacklistService blacklistService;
    private final NotificationWriter notifications;
    private final BlogProperties properties;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JoinService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
                       BlogJoinRequestRepository requestRepository, UserRepository userRepository,
                       BlogPermissionService permissionService, BlogLimitPolicy limitPolicy,
                       BlacklistService blacklistService, NotificationWriter notifications,
                       BlogProperties properties, JdbcTemplate jdbc, Clock clock) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.limitPolicy = limitPolicy;
        this.blacklistService = blacklistService;
        this.notifications = notifications;
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record JoinRequestView(Long id, Long blogId, String status, LocalDateTime requestedAt,
                                  LocalDateTime processedAt) {

        static JoinRequestView of(BlogJoinRequest r) {
            // 차단 자동 거절과 폐쇄 자동 거절은 신청자에게 일반 거절과 같게 보인다(D-98 #12).
            String status = r.getStatus() == JoinRequestStatus.AUTO_REJECTED ? "REJECTED" : r.getStatus().name();
            return new JoinRequestView(r.getId(), r.getBlogId(), status, r.getRequestedAt(), r.getProcessedAt());
        }
    }

    public record PendingRequestView(Long id, Long userId, String nickname, LocalDateTime requestedAt) {}

    /**
     * 참여 신청. 신청자 회원 행을 먼저 잠가 같은 회원이 동시에 신청해도 대기 신청이 하나만 생긴다.
     * 확인 순서는 research R-08.
     */
    @Transactional
    public JoinRequestView apply(long blogId, long userId) {
        limitPolicy.lockUser(userId);
        User user = userRepository.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (user.isAdmin()) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_ALLOWED);
        }
        BlogContext ctx = permissionService.context(blogRepository.findById(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND)), userId);
        if (ctx.member() != null) {
            throw new BusinessException(ctx.member().isOwner() ? ErrorCode.ALREADY_OWNER : ErrorCode.ALREADY_MEMBER);
        }
        if (blacklistService.isBlacklisted(blogId, user)) {
            throw new BusinessException(ErrorCode.BLOG_BLACKLISTED)
                    .with("appealAllowed", blacklistService.appealAllowed(blogId, userId));
        }
        if (requestRepository.findFirstByBlogIdAndUserIdAndStatus(blogId, userId, JoinRequestStatus.PENDING)
                .isPresent()) {
            throw new BusinessException(ErrorCode.JOIN_PENDING_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        requestRepository.findFirstByBlogIdAndUserIdAndStatusInOrderByProcessedAtDesc(blogId, userId, REJECTED)
                .map(r -> r.getProcessedAt().plus(properties.join().reapplyAfter()))
                .filter(reapplyAt -> now.isBefore(reapplyAt))
                .ifPresent(reapplyAt -> {
                    throw new BusinessException(ErrorCode.JOIN_REAPPLY_TOO_SOON).with("reapplyAt", reapplyAt);
                });

        Blog blog = ctx.blog();
        Long ownerId = ownerId(blogId);
        if (ownerId != null && blockedByOwner(ownerId, userId)) {
            BlogJoinRequest r = requestRepository.save(
                    BlogJoinRequest.processed(blogId, userId, JoinRequestStatus.AUTO_REJECTED, now));
            notifyResult(r, blog, false);
            return JoinRequestView.of(r);
        }
        if (blog.getJoinPolicy() == JoinPolicy.FREE) {
            BlogJoinRequest r = requestRepository.save(
                    BlogJoinRequest.processed(blogId, userId, JoinRequestStatus.APPROVED, now));
            addMember(blogId, userId, now);
            return JoinRequestView.of(r);
        }
        BlogJoinRequest r = requestRepository.save(BlogJoinRequest.pending(blogId, userId, now));
        for (Long recipient : approvers(blogId)) {
            notifications.write(recipient, NotificationType.JOIN_REQUESTED, "BLOG", blogId, userId,
                    "'" + blog.getName() + "' 블로그에 " + user.getNickname() + "님이 참여를 신청했습니다.",
                    "JOIN_REQUESTED:" + r.getId());
        }
        return JoinRequestView.of(r);
    }

    @Transactional
    public JoinRequestView approve(long blogId, long requestId, long managerId) {
        return process(blogId, requestId, managerId, true);
    }

    @Transactional
    public JoinRequestView reject(long blogId, long requestId, long managerId) {
        return process(blogId, requestId, managerId, false);
    }

    /** 블로그 행을 잠그고 다시 확인해, 두 관리자가 동시에 처리해도 한 번만 처리된다. */
    private JoinRequestView process(long blogId, long requestId, long managerId, boolean approve) {
        Blog blog = blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        permissionService.require(blog, managerId, BlogPermission.MEMBER_MANAGE);
        BlogJoinRequest r = requestRepository.findById(requestId)
                .filter(x -> x.getBlogId().equals(blogId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!r.isPending()) {
            throw new BusinessException(ErrorCode.JOIN_NOT_PENDING);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        r.process(approve ? JoinRequestStatus.APPROVED : JoinRequestStatus.REJECTED, managerId, now);
        if (approve && memberRepository.findByBlogIdAndUserId(blogId, r.getUserId()).isEmpty()) {
            addMember(blogId, r.getUserId(), now);
        }
        notifyResult(r, blog, approve);
        return JoinRequestView.of(r);
    }

    /** 신청자 본인이 대기 중인 신청을 취소한다(FR-020). 취소 뒤 바로 다시 신청할 수 있다. */
    @Transactional
    public void cancel(long requestId, long userId) {
        limitPolicy.lockUser(userId);
        BlogJoinRequest r = requestRepository.findById(requestId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!r.isPending()) {
            throw new BusinessException(ErrorCode.JOIN_NOT_PENDING);
        }
        r.process(JoinRequestStatus.CANCELED, userId, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public Page<PendingRequestView> pending(long blogId, int page, int size) {
        Page<BlogJoinRequest> requests = requestRepository.findAllByBlogIdAndStatusOrderByRequestedAtAscIdAsc(
                blogId, JoinRequestStatus.PENDING, PageRequest.of(page - 1, size));
        Map<Long, String> nicknames = userRepository.findAllById(
                        requests.getContent().stream().map(BlogJoinRequest::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() == null ? "" : u.getNickname()));
        return requests.map(r -> new PendingRequestView(r.getId(), r.getUserId(), nicknames.get(r.getUserId()),
                r.getRequestedAt()));
    }

    /** 폐쇄될 때 대기 중인 신청을 자동 거절하고 알린다(D-98 #15). BlogClosingService가 부른다. */
    public int rejectAllPendingOnClose(Blog blog, LocalDateTime now) {
        List<BlogJoinRequest> pending = requestRepository.findAllByBlogIdAndStatus(blog.getId(),
                JoinRequestStatus.PENDING);
        for (BlogJoinRequest r : pending) {
            r.process(JoinRequestStatus.AUTO_REJECTED, null, now);
            notifications.write(r.getUserId(), NotificationType.BLOG_CLOSED_REQUEST_REJECTED, "BLOG", blog.getId(),
                    null, "'" + blog.getName() + "' 블로그가 폐쇄되어 참여 신청이 거절되었습니다.",
                    "JOIN_CLOSED:" + r.getId());
        }
        return pending.size();
    }

    private void addMember(long blogId, long userId, LocalDateTime now) {
        memberRepository.save(BlogMember.member(blogId, userId, now));
        blogRepository.addMemberCount(blogId, 1);
    }

    private void notifyResult(BlogJoinRequest r, Blog blog, boolean approved) {
        notifications.write(r.getUserId(), approved ? NotificationType.JOIN_APPROVED : NotificationType.JOIN_REJECTED,
                "BLOG", blog.getId(), null,
                "'" + blog.getName() + "' 블로그 참여 신청이 " + (approved ? "승인" : "거절") + "되었습니다.",
                "JOIN_RESULT:" + r.getId());
    }

    private Long ownerId(long blogId) {
        return jdbc.query("SELECT user_id FROM blog_members WHERE blog_id = ? AND role = 'OWNER'",
                rs -> rs.next() ? rs.getLong(1) : null, blogId);
    }

    private boolean blockedByOwner(long ownerId, long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM user_blocks WHERE blocker_id = ? AND blocked_id = ?",
                Integer.class, ownerId, userId);
        return n != null && n > 0;
    }

    /** 블로그장과 멤버 관리 권한 부블로그장(3.6 "승인 권한이 있는 부블로그장", D-98 #18). */
    private List<Long> approvers(long blogId) {
        return jdbc.queryForList("SELECT user_id FROM blog_members WHERE blog_id = ? "
                + "AND (role = 'OWNER' OR (role = 'MANAGER' AND perm_member_manage = TRUE))", Long.class, blogId);
    }
}
