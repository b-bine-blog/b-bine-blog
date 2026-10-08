package com.mainblog.auth;

import static com.mainblog.support.AuthTestSupport.loginJson;
import static com.mainblog.support.AuthTestSupport.postJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mainblog.auth.application.CaptchaVerifier;
import com.mainblog.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/** 로그인 (T033, US2 인수 시나리오 1~4, 9, 10, SC-003, D-97, FR-021). */
class LoginIntegrationTest extends IntegrationTestBase {

    static final String PASSWORD = "Abcd123!";

    @MockitoBean
    CaptchaVerifier captchaVerifier;

    String ip;

    @BeforeEach
    void setUp() {
        ip = nextIp();
        when(captchaVerifier.verify(eq("pass"), any())).thenReturn(true);
        when(captchaVerifier.verify(eq("fail"), any())).thenReturn(false);
    }

    private ResultActions login(String email, String password, String captcha) throws Exception {
        return mvc.perform(postJson("/api/auth/login", loginJson(email, password, false, captcha))
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    @Test
    void 시나리오1_로그인하면_AT_RT_쿠키와_세션_정보() throws Exception {
        long id = createUser("user@x.com", PASSWORD, "회원닉");
        login("user@x.com", PASSWORD, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(id))
                .andExpect(jsonPath("$.nickname").value("회원닉"))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(cookie().exists("AT"))
                .andExpect(cookie().exists("RT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void 시나리오2_대문자_이메일로도_같은_계정() throws Exception {
        long id = createUser("user@x.com", PASSWORD, "회원닉");
        login("USER@x.com", PASSWORD, null).andExpect(status().isOk()).andExpect(jsonPath("$.memberId").value(id));
    }

    @Test
    void 시나리오3_SC003_3번_실패_뒤에는_사람_확인이_있어야_처리한다() throws Exception {
        createUser("user@x.com", PASSWORD, "회원닉");
        for (int i = 1; i <= 2; i++) {
            login("user@x.com", "Wrong123!", null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.captchaRequired").value(false));
        }
        login("user@x.com", "Wrong123!", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.captchaRequired").value(true));

        login("user@x.com", PASSWORD, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
        login("user@x.com", PASSWORD, "fail")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_FAILED"));
        login("user@x.com", PASSWORD, "pass").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_failures", Integer.class)).isZero();
    }

    @Test
    void 시나리오4_SC003_5번_실패면_5분_잠금_잠금_중에는_맞는_비밀번호도_거부() throws Exception {
        createUser("user@x.com", PASSWORD, "회원닉");
        for (int i = 1; i <= 3; i++) {
            login("user@x.com", "Wrong123!", null).andExpect(status().isUnauthorized());
        }
        login("user@x.com", "Wrong123!", "pass").andExpect(status().isUnauthorized());
        login("user@x.com", "Wrong123!", "pass")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("LOCKED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(300));

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        login("user@x.com", PASSWORD, "pass").andExpect(status().isLocked());

        clock.advance(Duration.ofSeconds(1));
        // 잠금이 풀리면 횟수도 0이라 사람 확인 없이 된다(D-97)
        login("user@x.com", PASSWORD, null).andExpect(status().isOk());
    }

    @Test
    void D97_없는_이메일도_같은_응답과_같은_잠금_안내() throws Exception {
        createUser("real@x.com", PASSWORD, "회원닉");
        for (int i = 1; i <= 5; i++) {
            String captcha = i > 3 ? "pass" : null;
            MockHttpServletResponse real = login("real@x.com", "Wrong123!", captcha).andReturn().getResponse();
            MockHttpServletResponse ghost = login("ghost@x.com", "Wrong123!", captcha).andReturn().getResponse();
            assertThat(ghost.getStatus()).isEqualTo(real.getStatus());
            assertThat(ghost.getContentAsString()).isEqualTo(real.getContentAsString());
        }
        login("ghost@x.com", "Wrong123!", null)
                .andExpect(status().isLocked());
    }

    @Test
    void 실패_문구는_이메일과_비밀번호를_구별하지_않는다() throws Exception {
        createUser("real@x.com", PASSWORD, "회원닉");
        login("real@x.com", "Wrong123!", null)
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 맞지 않습니다"));
        login("none@x.com", PASSWORD, null)
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 맞지 않습니다"));
    }

    @Test
    void 탈퇴한_회원은_로그인할_수_없다() throws Exception {
        long id = createUser("bye@x.com", PASSWORD, "떠난이");
        jdbc.update("UPDATE users SET status='WITHDRAWN', withdrawn_email=email, email=NULL WHERE id=?", id);
        login("bye@x.com", PASSWORD, null).andExpect(status().isUnauthorized());
    }

    @Test
    void FR021_같은_IP_21번째는_429와_Retry_After() throws Exception {
        for (int i = 1; i <= 20; i++) {
            login("user" + i + "@x.com", "Wrong123!", null).andExpect(status().isUnauthorized());
        }
        login("user21@x.com", "Wrong123!", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().string("Retry-After", "600"));
    }

    @Test
    void 시나리오10_블로그에서_정지된_회원도_로그인된다() throws Exception {
        long id = createUser("held@x.com", PASSWORD, "정지회원");
        jdbc.update("INSERT INTO blogs (name, address, visibility, join_policy, status) "
                + "VALUES ('블로그', 'held-blog', 'PUBLIC', 'FREE', 'ACTIVE')");
        Long blogId = jdbc.queryForObject("SELECT id FROM blogs", Long.class);
        jdbc.update("INSERT INTO blog_sanctions (blog_id, user_id, handled_by, sanction_type, suspend_period, reason, "
                + "starts_at, ends_at) VALUES (?, ?, ?, 'SUSPEND', 'DAYS_3', '사유', ?, ?)",
                blogId, id, id, BASE_TIME, BASE_TIME.plusDays(3));
        login("held@x.com", PASSWORD, null).andExpect(status().isOk());
    }
}
