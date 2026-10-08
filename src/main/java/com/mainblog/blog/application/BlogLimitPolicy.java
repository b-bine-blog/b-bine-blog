package com.mainblog.blog.application;

import com.mainblog.blog.domain.Visibility;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 블로그 생성 개수 제한 (T023, BLG-10, FR-010~FR-013, D-51, D-52, D-68, research R-02).
 * 회원 행을 잠근 뒤 지금 블로그장인 운영 중·폐쇄 예정 블로그를 공개(PUBLIC+LINK)·비공개로 나눠 센다.
 * 같은 회원의 동시 요청은 잠금 때문에 하나씩 처리되어 제한을 넘지 않는다. 반드시 트랜잭션 안에서 부른다.
 */
@Component
public class BlogLimitPolicy {

    private final JdbcTemplate jdbc;
    private final BlogProperties properties;

    public BlogLimitPolicy(JdbcTemplate jdbc, BlogProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /**
     * 회원 행 잠금. 잠금 순서는 users → blogs(research R-17).
     * 트랜잭션에서 가장 먼저 불러야 한다. 일반 SELECT를 먼저 하면 그때 잡힌 스냅샷 때문에 잠금 뒤에도 예전 개수를 센다.
     */
    public void lockUser(long userId) {
        jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
    }

    /**
     * 블로그 하나를 이 공개 범위로 더 가질 수 있는지. 공개 범위를 바꿀 때는 바꾸는 블로그를 빼고 센다(excludeBlogId).
     * 넘으면 409 BLOG_LIMIT_EXCEEDED.
     */
    public void checkCanHave(long userId, Visibility visibility, Long excludeBlogId) {
        boolean isPublic = visibility != Visibility.PRIVATE;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM blog_members m JOIN blogs b ON b.id = m.blog_id
                WHERE m.user_id = ? AND m.role = 'OWNER' AND b.status IN ('ACTIVE', 'CLOSING')
                  AND (? IS NULL OR b.id <> ?)
                  AND (CASE WHEN b.visibility = 'PRIVATE' THEN FALSE ELSE TRUE END) = ?""",
                Integer.class, userId, excludeBlogId, excludeBlogId, isPublic);
        int limit = isPublic ? properties.limit().publicLimit() : properties.limit().privateLimit();
        if (count != null && count >= limit) {
            throw new BusinessException(ErrorCode.BLOG_LIMIT_EXCEEDED)
                    .with("visibility", isPublic ? "PUBLIC" : "PRIVATE")
                    .with("limit", limit);
        }
    }
}
