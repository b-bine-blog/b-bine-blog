package com.mainblog.blog.application;

import com.mainblog.blog.application.BlogViews.BlogSummary;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 구독, 002 최소 범위 (T043, SOC-01, FR-029, FR-031, research R-15).
 * 새 구독자 알림과 구독 화면은 003에서 다듬는다.
 */
@Service
public class SubscriptionService {

    private final JdbcTemplate jdbc;
    private final BlogPermissionService permissionService;
    private final BlogQueryService queryService;

    public SubscriptionService(JdbcTemplate jdbc, BlogPermissionService permissionService,
                               BlogQueryService queryService) {
        this.jdbc = jdbc;
        this.permissionService = permissionService;
        this.queryService = queryService;
    }

    /** 볼 수 있는 블로그만 구독한다. 이미 구독 중이면 그대로 둔다. */
    @Transactional
    public void subscribe(long blogId, long userId) {
        permissionService.require(blogId, BlogPermission.VIEW);
        jdbc.update("INSERT IGNORE INTO blog_subscriptions (blog_id, user_id) VALUES (?, ?)", blogId, userId);
    }

    /** 구독하지 않았거나 블로그가 없어도 204. */
    @Transactional
    public void unsubscribe(long blogId, long userId) {
        jdbc.update("DELETE FROM blog_subscriptions WHERE blog_id = ? AND user_id = ?", blogId, userId);
    }

    @Transactional(readOnly = true)
    public List<BlogSummary> mySubscriptions(long userId) {
        return queryService.subscribedBlogs(userId);
    }

    /** 비공개 전환 알림 대상(FR-031). */
    @Transactional(readOnly = true)
    public List<Long> subscriberIds(long blogId) {
        return jdbc.queryForList("SELECT user_id FROM blog_subscriptions WHERE blog_id = ? ORDER BY id", Long.class,
                blogId);
    }
}
