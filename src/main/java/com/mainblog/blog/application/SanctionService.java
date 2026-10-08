package com.mainblog.blog.application;

import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogBlacklist;
import com.mainblog.blog.domain.BlogBlacklistRepository;
import com.mainblog.blog.domain.BlogMember;
import com.mainblog.blog.domain.BlogMemberRepository;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.blog.domain.BlogSanction;
import com.mainblog.blog.domain.BlogSanctionRepository;
import com.mainblog.blog.domain.SanctionType;
import com.mainblog.blog.domain.SuspendPeriod;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멤버 경고·정지·정지 해제·강제 퇴장 (T061, BLG-11, BLG-13, FR-048~FR-051, FR-053~FR-057, research R-10).
 * 모든 처리는 blog_sanctions에 한 행씩 쌓고 고치지 않는다. 대상에게는 끌 수 없는 알림을 보낸다.
 */
@Service
public class SanctionService {

    private static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogSanctionRepository sanctionRepository;
    private final BlogBlacklistRepository blacklistRepository;
    private final UserRepository userRepository;
    private final BlogPermissionService permissionService;
    private final MemberManageService memberManageService;
    private final SuspensionQuery suspensionQuery;
    private final BlacklistHasher hasher;
    private final NotificationWriter notifications;
    private final JdbcTemplate jdbc;
    private final BlogProperties properties;
    private final Clock clock;

    public SanctionService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
                           BlogSanctionRepository sanctionRepository, BlogBlacklistRepository blacklistRepository,
                           UserRepository userRepository, BlogPermissionService permissionService,
                           MemberManageService memberManageService, SuspensionQuery suspensionQuery,
                           BlacklistHasher hasher, NotificationWriter notifications, JdbcTemplate jdbc,
                           BlogProperties properties, Clock clock) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.sanctionRepository = sanctionRepository;
        this.blacklistRepository = blacklistRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.memberManageService = memberManageService;
        this.suspensionQuery = suspensionQuery;
        this.hasher = hasher;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    public record Command(SanctionType type, SuspendPeriod period, String reason) {}

    @Transactional
    public SanctionView sanction(long blogId, long actorId, long targetUserId, Command cmd) {
        Blog blog = blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        return apply(blog, actorId, targetUserId, cmd, null);
    }

    /**
     * 블로그 행을 잠근 트랜잭션에서 부른다(신고 처리도 여기로 온다).
     * 블로그장은 대상이 될 수 없고, 부블로그장은 블로그장만 제재할 수 있다(FR-048, D-98 #20).
     */
    SanctionView apply(Blog blog, long actorId, long targetUserId, Command cmd, Long reportId) {
        BlogContext ctx = permissionService.require(blog, actorId, BlogPermission.MEMBER_MANAGE);
        if (cmd.type() == null) {
            throw new InvalidInputException("type", "NotNull");
        }
        String reason = cmd.reason() == null || cmd.reason().isBlank() ? null : cmd.reason().strip();
        if (reason != null && reason.codePointCount(0, reason.length()) > 500) {
            throw new InvalidInputException("reason", "Size");
        }
        BlogMember target = memberRepository.findByBlogIdAndUserId(blog.getId(), targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (target.isOwner() || targetUserId == actorId || (target.isManager() && !ctx.member().isOwner())) {
            throw new BusinessException(ErrorCode.BLOG_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean suspended = suspensionQuery.find(blog.getId(), targetUserId, now).isPresent();
        switch (cmd.type()) {
            case SUSPEND -> {
                if (cmd.period() == null) {
                    throw new InvalidInputException("period", "NotNull");
                }
                if (suspended) {
                    throw new BusinessException(ErrorCode.ALREADY_SUSPENDED);
                }
            }
            case UNSUSPEND -> {
                if (!suspended) {
                    throw new BusinessException(ErrorCode.NOT_SUSPENDED);
                }
            }
            default -> { }
        }
        BlogSanction s = sanctionRepository.saveAndFlush(BlogSanction.of(blog.getId(), targetUserId, cmd.type(),
                cmd.period(), reason, actorId, reportId, now));
        if (cmd.type() == SanctionType.KICK) {
            kick(blog, target, s);
        }
        notifyTarget(blog, s);
        return view(s);
    }

    /** 멤버에서 빼고 글을 "탈퇴한 계정"으로 바꾸고 이름·이메일·전화번호를 해시로 블랙리스트에 올린다(FR-056, FR-057). */
    private void kick(Blog blog, BlogMember target, BlogSanction s) {
        memberManageService.removeMember(blog, target);
        User user = userRepository.findById(target.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        blacklistRepository.save(new BlogBlacklist(blog.getId(), s.getId(), hasher.name(user.getName()),
                hasher.email(user.getEmail()), hasher.phone(user.getPhone())));
    }

    /** 그 블로그의 기록만 최신순. 정지 횟수는 최근 1년(FR-053, FR-055). */
    @Transactional(readOnly = true)
    public History history(long blogId, long targetUserId) {
        permissionService.require(blogId, BlogPermission.MEMBER_MANAGE);
        List<SanctionView> items = sanctionRepository.findAllByBlogIdAndUserIdOrderByIdDesc(blogId, targetUserId)
                .stream().map(this::view).toList();
        if (items.isEmpty() && memberRepository.findByBlogIdAndUserId(blogId, targetUserId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        int count = suspendCount(blogId, targetUserId);
        return new History(items, count >= properties.sanction().kickSuggestCount(), count);
    }

    public int suspendCount(long blogId, long userId) {
        return suspensionQuery.countInWindow(blogId, userId,
                LocalDateTime.now(clock).minus(properties.sanction().countWindow()));
    }

    private void notifyTarget(Blog blog, BlogSanction s) {
        String name = "'" + blog.getName() + "' 블로그";
        String reason = s.getReason() == null ? "" : " 사유: " + s.getReason();
        NotificationType type;
        String content;
        switch (s.getSanctionType()) {
            case WARNING -> {
                type = NotificationType.MEMBER_WARNED;
                content = name + "에서 경고를 받았습니다." + reason;
            }
            case SUSPEND -> {
                type = NotificationType.MEMBER_SUSPENDED;
                content = name + (s.getEndsAt() == null ? "에서 영구 정지되었습니다."
                        : "에서 " + s.getEndsAt().format(SHOW_TIME) + "까지 정지되었습니다.") + reason;
            }
            case UNSUSPEND -> {
                type = NotificationType.MEMBER_UNSUSPENDED;
                content = name + " 정지가 해제되었습니다.";
            }
            default -> {
                type = NotificationType.MEMBER_KICKED;
                content = name + "에서 강제 퇴장되었습니다." + reason;
            }
        }
        notifications.write(s.getUserId(), type, "BLOG", blog.getId(), null, content, type.name() + ":" + s.getId());
    }

    private SanctionView view(BlogSanction s) {
        String nickname = jdbc.query("SELECT nickname FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, s.getHandledBy());
        return new SanctionView(s.getId(), s.getSanctionType().name(),
                s.getSuspendPeriod() == null ? null : s.getSuspendPeriod().name(), s.getReason(), s.getStartsAt(),
                s.getEndsAt(), nickname, s.getReportId());
    }

    public record SanctionView(Long id, String type, String period, String reason, LocalDateTime startsAt,
                               LocalDateTime endsAt, String handledByNickname, Long reportId) {}

    public record History(List<SanctionView> items, boolean kickSuggested, int suspendCountInWindow) {}
}
