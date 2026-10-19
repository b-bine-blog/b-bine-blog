package com.mainblog.contract;

import static com.mainblog.support.AuthTestSupport.cookieValue;
import static com.mainblog.support.AuthTestSupport.loginJson;
import static com.mainblog.support.AuthTestSupport.postJson;
import static com.mainblog.support.AuthTestSupport.setCookieHeader;
import static com.mainblog.support.AuthTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mainblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** 로그인 API 계약 (T035). contracts의 login 태그와 상태 코드·본문·Set-Cookie 속성. */
class LoginContractTest extends IntegrationTestBase {

    private MockHttpServletResponse login() throws Exception {
        createUser("c@x.com", "Abcd123!", "계약");
        String ip = nextIp();
        return mvc.perform(postJson("/api/auth/login", loginJson("c@x.com", "Abcd123!", true, null))
                        .with(r -> { r.setRemoteAddr(ip); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").isNumber())
                .andExpect(jsonPath("$.nickname").value("계약"))
                .andExpect(jsonPath("$.profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse();
    }

    @Test
    void 로그인_쿠키는_HttpOnly_Secure_SameSite_Lax() throws Exception {
        MockHttpServletResponse res = login();
        for (String name : new String[] {"AT", "RT"}) {
            assertThat(setCookieHeader(res, name))
                    .contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/");
        }
        assertThat(res.getContentAsString()).doesNotContain(cookieValue(res, "RT"));
    }

    @Test
    void 로그인_실패_본문은_LoginError() throws Exception {
        mvc.perform(postJson("/api/auth/login", loginJson("none@x.com", "x", false, null))
                        .with(r -> { r.setRemoteAddr(nextIp()); return r; }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.captchaRequired").value(false));
        mvc.perform(postJson("/api/auth/login", "{\"email\":\"a@x.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void refresh_extend_logout_session_상태_코드() throws Exception {
        MockHttpServletResponse res = login();
        String rt = cookieValue(res, "RT");
        String at = cookieValue(res, "AT");

        mvc.perform(get("/api/auth/session").cookie(new Cookie("AT", at)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").isNumber());
        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(withCsrf(post("/api/auth/extend")).cookie(new Cookie("RT", rt)))
                .andExpect(status().isNoContent());
        mvc.perform(withCsrf(post("/api/auth/extend")))
                .andExpect(status().isUnauthorized());

        MockHttpServletResponse refreshed = mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(new Cookie("RT", rt)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn().getResponse();
        assertThat(cookieValue(refreshed, "AT")).isNotBlank();
        mvc.perform(withCsrf(post("/api/auth/refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(withCsrf(post("/api/auth/logout"))).andExpect(status().isNoContent());
    }

    @Test
    void 공개_설정은_turnstile_site_key와_카카오_공유_키만() throws Exception {
        mvc.perform(get("/api/public-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnstileSiteKey").value("1x00000000000000000000AA"))
                .andExpect(jsonPath("$.kakaoJsKey").value(""))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
