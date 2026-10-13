package com.mainblog.auth;

import static com.mainblog.support.AuthTestSupport.postJson;
import static com.mainblog.support.AuthTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mainblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/** 비밀번호 찾기·재설정 (T052, US4 인수 시나리오 1~6, SC-001, SC-002, SC-004). */
class PasswordResetIntegrationTest extends IntegrationTestBase {

    static final String PASSWORD = "Abcd123!";
    static final String NEW_PASSWORD = "Newpw456@";

    private MockHttpServletResponse sendCode(String email) throws Exception {
        return mvc.perform(postJson("/api/auth/password-reset/code", "{\"email\":\"" + email + "\"}"))
                .andReturn().getResponse();
    }

    private ResultActions verify(String email, String code) throws Exception {
        return mvc.perform(postJson("/api/auth/password-reset/verify",
                "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code)));
    }

    private ResultActions reset(String email, String code, String pw, String confirm) throws Exception {
        return mvc.perform(postJson("/api/auth/password-reset",
                "{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirm\":\"%s\"}"
                        .formatted(email, code, pw, confirm)));
    }

    private String receiveCode(String email) throws Exception {
        assertThat(sendCode(email).getStatus()).isEqualTo(202);
        awaitMails(1);
        return codeIn(lastMailTo(email));
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void 시나리오1_SC001_가입_미가입_탈퇴_응답이_같고_가입된_이메일에만_메일() throws Exception {
        createUser("user@x.com", PASSWORD, "회원닉");
        long gone = createUser("gone@x.com", PASSWORD, "탈퇴닉");
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_email = email, email = NULL, withdrawn_at = ? "
                + "WHERE id = ?", BASE_TIME, gone);

        MockHttpServletResponse known = sendCode("User@X.com");
        MockHttpServletResponse unknown = sendCode("nobody@x.com");
        MockHttpServletResponse withdrawn = sendCode("gone@x.com");
        for (MockHttpServletResponse res : new MockHttpServletResponse[] {known, unknown, withdrawn}) {
            assertThat(res.getStatus()).isEqualTo(202);
            assertThat(res.getContentAsString()).isEmpty();
        }
        awaitMails(1);
        assertThat(mailsAfterQuietPeriod()).hasSize(1);
        assertThat(codeIn(lastMailTo("user@x.com"))).matches("\\d{6}");
        // 미가입 이메일로 확인을 해도 가입 여부가 드러나지 않는다
        verify("nobody@x.com", "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_INVALID"));
    }

    @Test
    void SC002_1분_안에_다시_요청하면_메일이_가지_않고_1분_뒤에는_새_번호() throws Exception {
        createUser("user@x.com", PASSWORD, "회원닉");
        String first = receiveCode("user@x.com");
        clock.advance(Duration.ofSeconds(59));
        assertThat(sendCode("user@x.com").getStatus()).isEqualTo(202);
        assertThat(mailsAfterQuietPeriod()).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        sendCode("user@x.com");
        awaitMails(2);
        String second = codeIn(lastMailTo("user@x.com"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_codes", Integer.class)).isEqualTo(1);
        if (!first.equals(second)) {
            verify("user@x.com", first).andExpect(status().isBadRequest());
        }
        verify("user@x.com", second).andExpect(status().isOk());
    }

    @Test
    void 시나리오2_30분이_지나면_만료() throws Exception {
        createUser("user@x.com", PASSWORD, "회원닉");
        String code = receiveCode("user@x.com");
        clock.advance(Duration.ofMinutes(30));
        verify("user@x.com", code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_INVALID"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_codes", Integer.class)).isZero();
    }

    @Test
    void 시나리오3_5번째로_틀리면_무효() throws Exception {
        createUser("user@x.com", PASSWORD, "회원닉");
        String code = receiveCode("user@x.com");
        for (int i = 1; i <= 4; i++) {
            verify("user@x.com", wrong(code)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CODE_MISMATCH"))
                    .andExpect(jsonPath("$.remainingAttempts").value(5 - i));
        }
        verify("user@x.com", wrong(code)).andExpect(jsonPath("$.code").value("CODE_INVALID"));
        verify("user@x.com", code).andExpect(jsonPath("$.code").value("CODE_INVALID"));
    }

    @Test
    void 시나리오4_한_번_쓴_번호는_다시_쓸_수_없다() throws Exception {
        createUser("user@x.com", PASSWORD, "회원닉");
        String code = receiveCode("user@x.com");
        verify("user@x.com", code).andExpect(status().isOk());
        reset("user@x.com", code, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        reset("user@x.com", code, "Other789#", "Other789#").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_INVALID"));
        assertThat(passwordEncoder.matches(NEW_PASSWORD,
                jdbc.queryForObject("SELECT password_hash FROM users", String.class))).isTrue();
        loginAs("user@x.com", NEW_PASSWORD, false);
    }

    @Test
    void 시나리오5_두_값이_다르거나_규칙에_맞지_않으면_바뀌지_않는다() throws Exception {
        createUser("user@x.com", PASSWORD, "회원닉");
        String code = receiveCode("user@x.com");
        reset("user@x.com", code, NEW_PASSWORD, "Newpw456#").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPasswordConfirm"));
        reset("user@x.com", code, "short1!", "short1!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
        reset("user@x.com", code, "abcdefgh1", "abcdefgh1").andExpect(status().isBadRequest());
        assertThat(passwordEncoder.matches(PASSWORD,
                jdbc.queryForObject("SELECT password_hash FROM users", String.class))).isTrue();
        // 번호는 아직 쓰지 않았으므로 맞게 다시 하면 바뀐다
        reset("user@x.com", code, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void 시나리오6_SC004_다른_기기_로그아웃과_잠금_해제() throws Exception {
        long id = createUser("user@x.com", PASSWORD, "회원닉");
        LoginCookies deviceA = loginAs("user@x.com", PASSWORD, true);
        LoginCookies deviceB = loginAs("user@x.com", PASSWORD, false);
        jdbc.update("INSERT INTO login_failures (email, fail_count, last_failed_at, locked_until) "
                + "VALUES ('user@x.com', 5, ?, ?)", BASE_TIME, BASE_TIME.plusMinutes(5));

        String code = receiveCode("user@x.com");
        reset("user@x.com", code, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        for (LoginCookies device : new LoginCookies[] {deviceA, deviceB}) {
            mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(new Cookie("RT", device.rt())))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked = 0",
                Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_failures", Integer.class)).isZero();
        // 잠금이 풀려 바로 새 비밀번호로 로그인된다
        loginAs("user@x.com", NEW_PASSWORD, false);
    }

    @Test
    void email과_resetToken을_함께_보내거나_둘_다_없으면_400() throws Exception {
        mvc.perform(postJson("/api/auth/password-reset/verify", "{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(postJson("/api/auth/password-reset/verify",
                        "{\"email\":\"a@x.com\",\"resetToken\":\"t\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
