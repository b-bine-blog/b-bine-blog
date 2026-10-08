package com.mainblog.blog.application;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 진행 중 정지 (FR-050, research R-01, R-10).
 * 가장 최근 SUSPEND가 아직 끝나지 않았고(ends_at NULL 또는 미래) 그 뒤 UNSUSPEND가 없으면 정지 중이다.
 */
@Component
public class SuspensionQuery {

    private final JdbcTemplate jdbc;

    public SuspensionQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ActiveSuspension> find(long blogId, long userId, LocalDateTime now) {
        return Optional.ofNullable(jdbc.query("""
                SELECT s.id, s.ends_at, s.reason FROM blog_sanctions s
                WHERE s.blog_id = ? AND s.user_id = ? AND s.sanction_type = 'SUSPEND'
                  AND (s.ends_at IS NULL OR s.ends_at > ?)
                  AND NOT EXISTS (SELECT 1 FROM blog_sanctions u WHERE u.blog_id = s.blog_id
                                  AND u.user_id = s.user_id AND u.sanction_type = 'UNSUSPEND' AND u.id > s.id)
                ORDER BY s.id DESC LIMIT 1""",
                rs -> rs.next() ? new ActiveSuspension(rs.getLong("id"),
                        rs.getObject("ends_at", LocalDateTime.class), rs.getString("reason")) : null,
                blogId, userId, now));
    }

    /** 그 블로그에서 최근 count-window(1년) 안에 시작한 정지 수. 도중에 해제된 정지도 센다(FR-053, D-95, D-98 #21). */
    public int countInWindow(long blogId, long userId, LocalDateTime since) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM blog_sanctions WHERE blog_id = ? AND user_id = ? "
                + "AND sanction_type = 'SUSPEND' AND starts_at > ?", Integer.class, blogId, userId, since);
        return n == null ? 0 : n;
    }

    /** endsAt이 null이면 영구 정지. */
    public record ActiveSuspension(long sanctionId, LocalDateTime endsAt, String reason) {}
}
