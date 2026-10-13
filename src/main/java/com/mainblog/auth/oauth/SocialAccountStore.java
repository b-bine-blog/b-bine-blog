package com.mainblog.auth.oauth;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** social_accounts (008 FR-002, FR-010~FR-012). (서비스, 고유 번호)와 (회원, 서비스)는 DB UNIQUE로 막는다. */
@Repository
public class SocialAccountStore {

    private final JdbcTemplate jdbc;

    public SocialAccountStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findUserId(SocialProvider provider, String providerUserId) {
        return jdbc.query("SELECT user_id FROM social_accounts WHERE provider = ? AND provider_user_id = ?",
                (rs, i) -> rs.getLong(1), provider.name(), providerUserId).stream().findFirst();
    }

    /** UNIQUE에 걸리면 DuplicateKeyException(DataIntegrityViolationException). */
    public void insert(long userId, SocialProvider provider, String providerUserId, String email) {
        jdbc.update("INSERT INTO social_accounts (user_id, provider, provider_user_id, email) VALUES (?, ?, ?, ?)",
                userId, provider.name(), providerUserId, email);
    }

    public List<Connection> findByUser(long userId) {
        return jdbc.query("SELECT provider, created_at FROM social_accounts WHERE user_id = ? ORDER BY id",
                (rs, i) -> new Connection(SocialProvider.valueOf(rs.getString(1)),
                        toLocal(rs.getTimestamp(2))), userId);
    }

    public int delete(long userId, SocialProvider provider) {
        return jdbc.update("DELETE FROM social_accounts WHERE user_id = ? AND provider = ?", userId, provider.name());
    }

    public int deleteAllByUser(long userId) {
        return jdbc.update("DELETE FROM social_accounts WHERE user_id = ?", userId);
    }

    public void deleteLink(SocialProvider provider, String providerUserId) {
        jdbc.update("DELETE FROM social_accounts WHERE provider = ? AND provider_user_id = ?",
                provider.name(), providerUserId);
    }

    private static LocalDateTime toLocal(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    public record Connection(SocialProvider provider, LocalDateTime connectedAt) {}
}
