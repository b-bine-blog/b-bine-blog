package com.mainblog.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DB 요청 제한 장부 (SCL-01, D-140). */
class JdbcRateLimiterTest extends IntegrationTestBase {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Autowired
    JdbcRateLimiter limiter;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 고정_창_안에서_한도까지만_받고_창이_지나면_다시_받는다() {
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("like:ip:1.1.1.1", 20, WINDOW)).isTrue();
        }
        assertThat(limiter.tryAcquire("like:ip:1.1.1.1", 20, WINDOW)).isFalse();
        assertThat(limiter.tryAcquire("like:ip:2.2.2.2", 20, WINDOW)).isTrue();

        clock.advance(WINDOW);
        assertThat(limiter.tryAcquire("like:ip:1.1.1.1", 20, WINDOW)).isTrue();
    }

    @Test
    void 키_원문은_저장하지_않고_해시만_저장한다() {
        limiter.tryAcquire("login:ip:10.0.0.7", 5, WINDOW);

        assertThat(jdbc.queryForList("SELECT key_hash FROM rate_limits", String.class))
                .containsExactly(JdbcRateLimiter.hash("login:ip:10.0.0.7"))
                .allSatisfy(hash -> assertThat(hash).hasSize(64).doesNotContain("10.0.0.7"));
    }

    @Test
    void 부른_쪽_트랜잭션이_되돌려져도_센_횟수는_남는다() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.executeWithoutResult(status -> {
            limiter.tryAcquire("comment:user:1", 1, WINDOW);
            status.setRollbackOnly();
        });

        assertThat(limiter.tryAcquire("comment:user:1", 1, WINDOW)).isFalse();
    }

    @Test
    void 다시_시도할_때까지_남은_초를_알려_준다() {
        assertThat(limiter.retryAfterSeconds("report:user:1", WINDOW)).isZero();

        limiter.tryAcquire("report:user:1", 1, WINDOW);
        clock.advance(Duration.ofSeconds(15));

        assertThat(limiter.retryAfterSeconds("report:user:1", WINDOW)).isEqualTo(45);
    }
}
