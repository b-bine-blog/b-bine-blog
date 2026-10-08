package com.mainblog.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** 로그인 쿠키의 Secure 설정 (FR-026). 기본은 켜 두고, 내 컴퓨터 실행에서만 끈다(auth.cookie-secure). */
class AuthCookiesTest {

    private List<String> written(boolean secure) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        new AuthCookies(null, secure).write(res, "a", "r", false, null);
        return res.getHeaders("Set-Cookie");
    }

    @Test
    void 기본은_Secure와_HttpOnly가_붙는다() {
        assertThat(written(true)).hasSize(2).allSatisfy(c -> assertThat(c).contains("Secure", "HttpOnly"));
    }

    @Test
    void 끄면_Secure만_빠지고_HttpOnly는_남는다() {
        assertThat(written(false)).hasSize(2).allSatisfy(c -> {
            assertThat(c).doesNotContain("Secure");
            assertThat(c).contains("HttpOnly", "SameSite=Lax");
        });
    }
}
