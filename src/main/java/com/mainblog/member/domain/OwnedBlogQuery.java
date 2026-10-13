package com.mainblog.member.domain;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 회원이 블로그장인 운영 중·폐쇄 예정 블로그 (T047, FR-046, D-15, D-18).
 * 블로그 엔티티는 002에서 만들므로 여기서는 읽기 전용 쿼리로만 본다. 역할은 요청마다 DB에서 확인한다(헌장 II).
 */
@Component
public class OwnedBlogQuery {

    private final JdbcTemplate jdbc;

    public OwnedBlogQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OwnedBlog> findByOwner(Long userId) {
        return jdbc.query("""
                SELECT b.id, b.name, b.address, b.status, b.close_scheduled_at
                FROM blog_members m JOIN blogs b ON b.id = m.blog_id
                WHERE m.user_id = ? AND m.role = 'OWNER' AND b.status IN ('ACTIVE', 'CLOSING')
                ORDER BY b.id""",
                (rs, i) -> new OwnedBlog(rs.getLong("id"), rs.getString("name"), rs.getString("address"),
                        rs.getString("status"), rs.getObject("close_scheduled_at", LocalDateTime.class)),
                userId);
    }

    public record OwnedBlog(Long blogId, String name, String address, String status, LocalDateTime closeScheduledAt) {}
}
