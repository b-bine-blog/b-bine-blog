package com.mainblog.common.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DB 요청 제한 (SCL-01, D-140). 앱이 2대(D-129)여도 같은 장부(rate_limits)를 보고 센다.
 * 고정 창: 창의 첫 요청 시각부터 window 동안 limit번까지 받는다. 키 원문(IP·회원 번호)은 SHA-256으로만 저장한다.
 * 부른 쪽 트랜잭션이 예외로 되돌려져도 센 횟수는 남아야 하므로 새 트랜잭션에서 센다.
 */
@Component
public class JdbcRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(JdbcRateLimiter.class);

    // MySQL은 SET을 왼쪽부터 고친 값으로 계산하므로 window_start를 맨 뒤에서 바꾼다
    private static final String COUNT = """
            INSERT INTO rate_limits (key_hash, window_start, request_count, expires_at) VALUES (?, ?, 1, ?)
            ON DUPLICATE KEY UPDATE
              request_count = IF(window_start <= ?, 1, request_count + 1),
              expires_at = IF(window_start <= ?, ?, expires_at),
              window_start = IF(window_start <= ?, ?, window_start)""";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public JdbcRateLimiter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime staleBefore = now.minus(window);
        LocalDateTime expiresAt = now.plus(window);
        String hash = hash(key);
        try {
            Integer count = transaction.execute(status -> {
                jdbc.update(COUNT, hash, now, expiresAt, staleBefore, staleBefore, expiresAt, staleBefore, now);
                return jdbc.queryForObject("SELECT request_count FROM rate_limits WHERE key_hash = ?",
                        Integer.class, hash);
            });
            return count != null && count <= limit;
        } catch (DataAccessException e) {
            // 장부를 못 쓰면 막지 않는다. 로그인은 login_failures 잠금(SEC-03)이 따로 지킨다
            log.warn("요청 제한을 세지 못해 통과시킴: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public long retryAfterSeconds(String key, Duration window) {
        List<LocalDateTime> starts = jdbc.queryForList(
                "SELECT window_start FROM rate_limits WHERE key_hash = ?", LocalDateTime.class, hash(key));
        if (starts.isEmpty()) {
            return 0;
        }
        long remaining = Duration.between(LocalDateTime.now(clock), starts.get(0).plus(window)).toMillis();
        return Math.max(1, (remaining + 999) / 1000);
    }

    /** 창이 끝난 행을 지운다. 04:00 정리 작업(DailyCleanupJob)이 부른다. */
    public int deleteExpired(LocalDateTime now) {
        return jdbc.update("DELETE FROM rate_limits WHERE expires_at < ?", now);
    }

    static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
