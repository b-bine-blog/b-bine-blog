package com.mainblog.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.auth.security.CodeHasher;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.ratelimit.InMemoryRateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** 2단계 기반 부품(T014, T019) 단위 확인. */
class RateLimiterAndHasherTest {

    @Test
    void 고정_창_요청_제한() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-08T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
        Duration window = Duration.ofMinutes(10);
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("login:1.2.3.4", 20, window)).isTrue();
        }
        assertThat(limiter.tryAcquire("login:1.2.3.4", 20, window)).isFalse();
        assertThat(limiter.tryAcquire("login:5.6.7.8", 20, window)).isTrue();
        clock.advance(window);
        assertThat(limiter.tryAcquire("login:1.2.3.4", 20, window)).isTrue();
    }

    @Test
    void 인증번호는_6자리이고_해시로_비교한다() {
        AuthProperties props = new AuthProperties("j", "secret", null, null, null, null,
                null, null, null, null, null, null, null);
        CodeHasher hasher = new CodeHasher(props);
        String code = hasher.newSixDigitCode();
        assertThat(code).matches("\\d{6}");
        String hash = hasher.hashCode(code);
        assertThat(hash).hasSize(64).isNotEqualTo(code);
        assertThat(hasher.matchesCode(code, hash)).isTrue();
        assertThat(hasher.matchesCode(code.equals("000000") ? "000001" : "000000", hash)).isFalse();
        assertThat(hasher.newRandomToken()).hasSize(43);
        assertThat(hasher.sha256("a")).hasSize(64);
    }
}
