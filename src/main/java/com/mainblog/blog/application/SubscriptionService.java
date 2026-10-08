package com.mainblog.blog.application;

import com.mainblog.blog.application.BlogViews.BlogSummary;
import com.mainblog.common.config.SocialProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import com.mainblog.common.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 구독 (002 T043, SOC-01, FR-029, FR-031, research R-15).
 * 003 T025(FR-007, FR-009, FR-040, D-90, D-102 #1·#2·#4, research R-11): 회원당 요청 제한(구독·취소 합쳐 셈),
 * 관리자 403, 블로그장 400, 새로 구독되면 블로그장에게 BLOG_SUBSCRIBED(다시 구독해도 한 번).
 */
@Service
public class SubscriptionService {

    private final JdbcTemplate jdbc;
    private final BlogPermissionService permissionService;
    private final BlogQueryService queryService;
    private final RateLimiter rateLimiter;
    private final NotificationWriter notifications;
    private final SocialProperties socialProperties;
    private final Clock clock;

    public SubscriptionService(JdbcTemplate jdbc, BlogPermissionService permissionService,
                               BlogQueryService queryService, RateLimiter rateLimiter,
                               NotificationWriter notifications, SocialProperties socialProperties, Clock clock) {
        this.jdbc = jdbc;
        this.permissionService = permissionService;
        this.queryService = queryService;
        this.rateLimiter = rateLimiter;
        this.notifications = notifications;
        this.socialProperties = socialProperties;
        this.clock = clock;
    }

    /**
     * 볼 수 있는 블로그만 구독한다(비공개는 멤버만, D-102 #2). 이미 구독 중이면 그대로 둔다.
     * 확인 순서: 요청 제한 → 관리자 → 볼 수 있는지 → 블로그장(research R-11).
     */
    @Transactional
    public void subscribe(long blogId, long userId) {
        limit(userId);
        String role = jdbc.query("SELECT role FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null,
                userId);
        if ("ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_ALLOWED);
        }
        BlogContext ctx = permissionService.require(blogId, BlogPermission.VIEW);
        if (ctx.member() != null && ctx.member().isOwner()) {
            throw new BusinessException(ErrorCode.SELF_NOT_ALLOWED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int added = jdbc.update("INSERT IGNORE INTO blog_subscriptions (blog_id, user_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?)", blogId, userId, now, now);
        if (added > 0) {
            notifyOwner(ctx, userId);
        }
    }

    /** 구독하지 않았거나 블로그가 없어도 204. 요청 제한은 구독과 합쳐 센다. */
    @Transactional
    public void unsubscribe(long blogId, long userId) {
        limit(userId);
        jdbc.update("DELETE FROM blog_subscriptions WHERE blog_id = ? AND user_id = ?", blogId, userId);
    }

    @Transactional(readOnly = true)
    public List<BlogSummary> mySubscriptions(long userId) {
        return queryService.subscribedBlogs(userId);
    }

    private void notifyOwner(BlogContext ctx, long userId) {
        Long ownerId = jdbc.query("SELECT user_id FROM blog_members WHERE blog_id = ? AND role = 'OWNER'",
                rs -> rs.next() ? rs.getLong(1) : null, ctx.blogId());
        String nickname = jdbc.query("SELECT nickname FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : "",
                userId);
        if (ownerId == null) {
            return;
        }
        notifications.write(ownerId, NotificationType.BLOG_SUBSCRIBED, "BLOG", ctx.blogId(), userId,
                "'" + ctx.blog().getName() + "' 블로그를 " + nickname + "님이 구독했습니다.",
                "BLOG_SUBSCRIBED:" + ctx.blogId() + ":" + userId);
    }

    private void limit(long userId) {
        String key = "subscribe:" + userId;
        SocialProperties.RateLimit rule = socialProperties.subscribe();
        if (!rateLimiter.tryAcquire(key, rule.rateLimit(), rule.rateWindow())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED)
                    .with("retryAfterSeconds", rateLimiter.retryAfterSeconds(key, rule.rateWindow()));
        }
    }

    /** 비공개 전환 알림 대상(FR-031). */
    @Transactional(readOnly = true)
    public List<Long> subscriberIds(long blogId) {
        return jdbc.queryForList("SELECT user_id FROM blog_subscriptions WHERE blog_id = ? ORDER BY id", Long.class,
                blogId);
    }
}
