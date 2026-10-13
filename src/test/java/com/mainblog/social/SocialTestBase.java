package com.mainblog.social;

import com.mainblog.board.BoardTestBase;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 003 소셜·알림 통합 테스트 도우미 (T007). 004 BoardTestBase에 알림·팔로우·차단·구독·신고 행 넣기를 더한다.
 * 데이터는 테스트마다 비워진다(IntegrationTestBase).
 */
public abstract class SocialTestBase extends BoardTestBase {

    /** 알림 한 건을 직접 넣고 번호를 돌려준다. */
    protected long insertNotification(long recipientId, String type, String tab, String targetType, Long targetId,
                                      Long actorId, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO notifications (recipient_id, notification_type, tab, target_type, target_id, actor_id, "
                        + "content, event_key, is_read, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?)",
                recipientId, type, tab, targetType, targetId, actorId, type + " 알림", "T:" + System.nanoTime(),
                createdAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM notifications", Long.class);
    }

    /** 대상 없이 받는 사람·종류·탭·받은 시각만 정한 알림. */
    protected long insertNotification(long recipientId, String type, String tab, LocalDateTime createdAt) {
        return insertNotification(recipientId, type, tab, null, null, null, createdAt);
    }

    /** n건을 1분 간격으로 넣는다(오래된 것부터). */
    protected void insertNotifications(long recipientId, String type, String tab, int n) {
        for (int i = 0; i < n; i++) {
            insertNotification(recipientId, type, tab, clock.now().minusMinutes(n - i));
        }
    }

    protected int notificationCount(long recipientId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id = ?", Integer.class,
                recipientId);
    }

    /** 그 회원이 받은 알림의 종류:탭, 받은 순. */
    protected List<String> notificationTabsOf(long recipientId) {
        return jdbc.queryForList("SELECT CONCAT(notification_type, ':', tab) FROM notifications WHERE recipient_id = ? "
                + "ORDER BY id", String.class, recipientId);
    }

    protected void follow(long followerId, long followeeId, LocalDateTime at) {
        jdbc.update("INSERT INTO follows (follower_id, followee_id, created_at) VALUES (?, ?, ?)", followerId,
                followeeId, at);
    }

    protected boolean follows(long followerId, long followeeId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM follows WHERE follower_id = ? AND followee_id = ?",
                Integer.class, followerId, followeeId) > 0;
    }

    protected boolean blocks(long blockerId, long blockedId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_blocks WHERE blocker_id = ? AND blocked_id = ?",
                Integer.class, blockerId, blockedId) > 0;
    }

    protected boolean subscribed(long blogId, long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM blog_subscriptions WHERE blog_id = ? AND user_id = ?",
                Integer.class, blogId, userId) > 0;
    }

    protected void withdraw(long userId) {
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_email = email, email = NULL, withdrawn_at = ? "
                + "WHERE id = ?", clock.now(), userId);
    }

    protected void setRetention(long userId, int days) {
        jdbc.update("INSERT INTO notification_settings (user_id, retention_days) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE retention_days = VALUES(retention_days)", userId, days);
    }

    protected String reportJson(String targetType, long targetId, Long blogId, String reason, String detail)
            throws Exception {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("blogId", blogId);
        body.put("reason", reason);
        body.put("detail", detail);
        return json(body);
    }
}
