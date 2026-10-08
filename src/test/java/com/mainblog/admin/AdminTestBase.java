package com.mainblog.admin;

import com.mainblog.social.SocialTestBase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 005 관리자 통합 테스트 도우미 (T011, research R-16). 003 SocialTestBase(→ 004 BoardTestBase) 위에
 * 관리자 로그인, 관리자 신고 행 직접 넣기, 경고 행 넣기, 활동 기록 읽기를 더한다.
 */
public abstract class AdminTestBase extends SocialTestBase {

    /** 관리자 신고(route = ADMIN)를 직접 넣는다. blogId가 null이면 메인 프로필 회원 신고. */
    protected long insertAdminReport(Long blogId, long reporterId, String targetType, long targetId) {
        return insertAdminReport(blogId, reporterId, targetType, targetId, clock.now());
    }

    protected long insertAdminReport(Long blogId, long reporterId, String targetType, long targetId,
                                     LocalDateTime createdAt) {
        jdbc.update("INSERT INTO reports (blog_id, reporter_id, target_type, target_id, target_snapshot, reason, "
                + "reason_detail, route, status, created_at) VALUES (?, ?, ?, ?, ?, 'ABUSE', '설명', 'ADMIN', 'PENDING', ?)",
                blogId, reporterId, targetType, targetId, "{\"name\":\"신고 당시 내용\"}", createdAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM reports", Long.class);
    }

    /** 블로그장 경고 행을 직접 넣는다. */
    protected long insertWarning(long blogId, long userId, long adminId, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO owner_warnings (blog_id, user_id, admin_id, reason, created_at) VALUES (?, ?, ?, ?, ?)",
                blogId, userId, adminId, "예전 경고", createdAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM owner_warnings", Long.class);
    }

    /** 활동 기록의 종류, 기록 순. */
    protected List<String> actionTypes() {
        return jdbc.queryForList("SELECT action_type FROM admin_actions ORDER BY id", String.class);
    }

    protected List<Map<String, Object>> actions(String type) {
        return jdbc.queryForList("SELECT * FROM admin_actions WHERE action_type = ? ORDER BY id", type);
    }

    protected int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    protected String ownerOf(long blogId) {
        List<Long> ids = jdbc.queryForList("SELECT user_id FROM blog_members WHERE blog_id = ? AND role = 'OWNER'",
                Long.class, blogId);
        return ids.isEmpty() ? null : String.valueOf(ids.get(0));
    }

    /** 그 회원이 받은 그 종류 알림의 내용, 받은 순. */
    protected List<String> notificationContents(long userId, String type) {
        return jdbc.queryForList("SELECT content FROM notifications WHERE recipient_id = ? AND notification_type = ? "
                + "ORDER BY id", String.class, userId, type);
    }
}
