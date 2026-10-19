package com.mainblog.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.auth.security.CodeHasher;
import com.mainblog.common.config.AuthProperties;
import org.junit.jupiter.api.Test;

/** 2단계 기반 부품(T014) 단위 확인. 요청 제한(T019)은 DB 장부라 JdbcRateLimiterTest에서 본다(D-140). */
class RateLimiterAndHasherTest {

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
