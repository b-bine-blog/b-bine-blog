package com.mainblog.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * 기반 확인 (T023).
 * 컨텍스트가 ddl-auto=validate로 뜨면 엔티티와 Crowfoot 스키마가 맞는 것이다(R-02).
 * 보안 헤더 5종(SEC-12)과 XSRF-TOKEN 쿠키(SEC-10)가 응답에 있는지 본다.
 */
class SchemaValidationTest extends IntegrationTestBase {

    @Test
    void 보안_헤더와_CSRF_쿠키가_응답에_있다() throws Exception {
        mvc.perform(get("/privacy.html").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; script-src 'self' https://challenges.cloudflare.com; "
                                + "frame-src 'self' https://challenges.cloudflare.com https://www.youtube-nocookie.com https://discord.com https://t.me https://www.google.com https://calendar.google.com https://player.twitch.tv; frame-ancestors 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void 로그인이_필요한_API는_401_UNAUTHENTICATED() throws Exception {
        mvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void CSRF_헤더_없는_POST는_거부된다() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());
    }
}
