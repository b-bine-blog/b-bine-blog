package com.mainblog.auth.oauth;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * social_pendings (008 FR-004, FR-005, FR-016). 브라우저에는 무작위 토큰만 주고 DB에는 해시만 둔다.
 * 끝나거나 만료되면 지우고, 남은 것은 04:00 정리 작업이 지운다.
 */
@Repository
public class SocialPendingStore {

    private final JdbcTemplate jdbc;

    public SocialPendingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(String tokenHash, SocialProvider provider, String providerUserId, String email,
                       Purpose purpose, boolean rememberMe, LocalDateTime expiresAt) {
        // 같은 소셜 계정으로 다시 시작하면 이전 대기는 버린다
        jdbc.update("DELETE FROM social_pendings WHERE provider = ? AND provider_user_id = ?",
                provider.name(), providerUserId);
        jdbc.update("INSERT INTO social_pendings (token_hash, provider, provider_user_id, email, purpose, remember_me,"
                        + " expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                tokenHash, provider.name(), providerUserId, email, purpose.name(), rememberMe, expiresAt);
    }

    /** 만료되지 않은 대기. 행을 잠가 같은 토큰의 동시 요청을 한 번만 받는다. */
    public Optional<Pending> findValidForUpdate(String tokenHash, LocalDateTime now) {
        return jdbc.query("SELECT id, provider, provider_user_id, email, purpose, remember_me, expires_at"
                        + " FROM social_pendings WHERE token_hash = ? AND expires_at > ? FOR UPDATE",
                (rs, i) -> new Pending(rs.getLong(1), SocialProvider.valueOf(rs.getString(2)), rs.getString(3),
                        rs.getString(4), Purpose.valueOf(rs.getString(5)), rs.getBoolean(6),
                        rs.getTimestamp(7).toLocalDateTime()),
                tokenHash, now).stream().findFirst();
    }

    public void setEmail(long id, String email) {
        jdbc.update("UPDATE social_pendings SET email = ? WHERE id = ?", email, id);
    }

    public void changeToLink(long id, String email, LocalDateTime expiresAt) {
        jdbc.update("UPDATE social_pendings SET email = ?, purpose = 'LINK', expires_at = ? WHERE id = ?",
                email, expiresAt, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM social_pendings WHERE id = ?", id);
    }

    public int deleteExpired(LocalDateTime now) {
        return jdbc.update("DELETE FROM social_pendings WHERE expires_at <= ?", now);
    }

    public enum Purpose { SIGNUP, LINK }

    public record Pending(long id, SocialProvider provider, String providerUserId, String email, Purpose purpose,
                          boolean rememberMe, LocalDateTime expiresAt) {}
}
