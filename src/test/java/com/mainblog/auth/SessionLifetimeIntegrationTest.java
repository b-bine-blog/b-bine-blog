package com.mainblog.auth;

import static com.mainblog.support.AuthTestSupport.cookieValue;
import static com.mainblog.support.AuthTestSupport.loginJson;
import static com.mainblog.support.AuthTestSupport.postJson;
import static com.mainblog.support.AuthTestSupport.setCookieHeader;
import static com.mainblog.support.AuthTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mainblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** 로그인 유지 (T034, US2 인수 시나리오 5~9, SC-004, SC-005, FR-023~FR-026, FR-029, D-97). */
class SessionLifetimeIntegrationTest extends IntegrationTestBase {

    long userId;

    @BeforeEach
    void setUp() {
        userId = createUser("user@x.com", "Abcd123!", "회원닉");
    }

    private MockHttpServletResponse login(boolean rememberMe) throws Exception {
        String ip = nextIp();
        return mvc.perform(postJson("/api/auth/login", loginJson("user@x.com", "Abcd123!", rememberMe, null))
                        .with(r -> { r.setRemoteAddr(ip); return r; }))
                .andExpect(status().isOk()).andReturn().getResponse();
    }

    private MockHttpServletResponse refresh(String rt) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(new Cookie("RT", rt)))
                .andReturn().getResponse();
    }

    @Test
    void 미체크_로그인_쿠키에는_Max_Age가_없다() throws Exception {
        MockHttpServletResponse res = login(false);
        assertThat(setCookieHeader(res, "AT")).doesNotContain("Max-Age");
        assertThat(setCookieHeader(res, "RT")).doesNotContain("Max-Age");
    }

    @Test
    void 체크한_로그인의_RT_쿠키는_14일() throws Exception {
        MockHttpServletResponse res = login(true);
        assertThat(setCookieHeader(res, "RT")).contains("Max-Age=1209600");
    }

    @Test
    void 시나리오5_SC005_미체크면_활동_없이_31분_뒤_refresh_401() throws Exception {
        String rt = cookieValue(login(false), "RT");
        clock.advance(Duration.ofMinutes(29));
        assertThat(refresh(rt).getStatus()).isEqualTo(204);
        // 회전된 새 RT도 활동 시각을 이어받아 31분 시점에는 만료
        clock.advance(Duration.ofMinutes(2));
        assertThat(refresh(rt).getStatus()).isEqualTo(401);
    }

    @Test
    void 시나리오6_활동_헤더_없는_요청만_있으면_31분_뒤_401() throws Exception {
        String rt = cookieValue(login(false), "RT");
        for (int minute = 5; minute <= 30; minute += 5) {
            clock.setTo(BASE_TIME.plusMinutes(minute));
            mvc.perform(get("/api/auth/session").cookie(new Cookie("RT", rt)));
        }
        clock.setTo(BASE_TIME.plusMinutes(31));
        assertThat(refresh(rt).getStatus()).isEqualTo(401);
    }

    @Test
    void SC005_활동_헤더가_있으면_무활동_시간이_다시_시작된다() throws Exception {
        String rt = cookieValue(login(false), "RT");
        clock.setTo(BASE_TIME.plusMinutes(20));
        mvc.perform(get("/api/auth/session").cookie(new Cookie("RT", rt)).header("X-User-Activity", "1"));
        clock.setTo(BASE_TIME.plusMinutes(45));
        assertThat(refresh(rt).getStatus()).isEqualTo(204);
    }

    @Test
    void 시나리오7_extend로_연장된다() throws Exception {
        String rt = cookieValue(login(false), "RT");
        clock.setTo(BASE_TIME.plusMinutes(25));
        mvc.perform(withCsrf(post("/api/auth/extend")).cookie(new Cookie("RT", rt)))
                .andExpect(status().isNoContent());
        clock.setTo(BASE_TIME.plusMinutes(50));
        assertThat(refresh(rt).getStatus()).isEqualTo(204);
    }

    @Test
    void 시나리오8_SC004_체크하면_13일째_유지되고_14일이_지나면_401_회전해도_만료는_그대로() throws Exception {
        String rt = cookieValue(login(true), "RT");
        LocalDateTime expires = jdbc.queryForObject("SELECT expires_at FROM refresh_tokens", LocalDateTime.class);
        assertThat(expires).isEqualTo(BASE_TIME.plusDays(14));

        clock.advance(Duration.ofDays(13));
        MockHttpServletResponse refreshed = refresh(rt);
        assertThat(refreshed.getStatus()).isEqualTo(204);
        String rotated = cookieValue(refreshed, "RT");
        assertThat(jdbc.queryForList("SELECT expires_at FROM refresh_tokens WHERE revoked = FALSE",
                LocalDateTime.class)).containsExactly(BASE_TIME.plusDays(14));
        assertThat(setCookieHeader(refreshed, "RT")).contains("Max-Age=86400");

        clock.setTo(BASE_TIME.plusDays(14).plusMinutes(1));
        assertThat(refresh(rotated).getStatus()).isEqualTo(401);
    }

    @Test
    void 시나리오9_로그아웃_전에_복사한_RT로는_다시_로그인_상태를_만들_수_없다() throws Exception {
        MockHttpServletResponse loggedIn = login(true);
        String rt = cookieValue(loggedIn, "RT");
        MockHttpServletResponse out = mvc.perform(withCsrf(post("/api/auth/logout")).cookie(new Cookie("RT", rt)))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(setCookieHeader(out, "AT")).contains("Max-Age=0");
        assertThat(setCookieHeader(out, "RT")).contains("Max-Age=0");
        assertThat(refresh(rt).getStatus()).isEqualTo(401);
    }

    @Test
    void 회전_전_RT를_다시_쓰면_그_회원의_RT가_모두_폐기된다() throws Exception {
        String first = cookieValue(login(true), "RT");
        String second = cookieValue(refresh(first), "RT");
        assertThat(refresh(first).getStatus()).isEqualTo(401);
        assertThat(refresh(second).getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE revoked = FALSE AND user_id = ?",
                Integer.class, userId)).isZero();
    }

    @Test
    void AT는_15분_뒤_만료된다() throws Exception {
        String at = cookieValue(login(false), "AT");
        mvc.perform(get("/api/auth/session").cookie(new Cookie("AT", at))).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        mvc.perform(get("/api/auth/session").cookie(new Cookie("AT", at))).andExpect(status().isUnauthorized());
    }
}
