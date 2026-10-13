package com.mainblog.auth;

import static com.mainblog.support.AuthTestSupport.postJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mainblog.support.IntegrationTestBase;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** 이메일 찾기에서 비밀번호 재설정까지 (T066, US6 인수 시나리오 1~7, SC-006, SC-007). */
class FindEmailIntegrationTest extends IntegrationTestBase {

    static final String PASSWORD = "Abcd123!";
    static final String NEW_PASSWORD = "Newpw456@";

    String ip;

    @BeforeEach
    void setUp() {
        ip = nextIp();
    }

    private ResultActions find(String name, String phone) throws Exception {
        return mvc.perform(postJson("/api/auth/find-email", "{\"name\":\"%s\",\"phone\":\"%s\"}".formatted(name, phone))
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    private String findBody(String name, String phone) throws Exception {
        return find(name, phone).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions resetCode(String json) throws Exception {
        return mvc.perform(postJson("/api/auth/find-email/reset-code", json));
    }

    private long user(String email, String nickname, String joinedOn) {
        long id = createUser(email, PASSWORD, nickname);
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", joinedOn + " 10:00:00", id);
        return id;
    }

    @Test
    void 시나리오1_SC006_공백과_하이픈을_정리해_가린_이메일과_가입일을_보여준다() throws Exception {
        user("abcdef@gmail.com", "길동", "2026-10-02");
        String body = findBody(" 홍길동 ", "010-1234-5678");
        assertThat(JsonPath.<List<?>>read(body, "$.accounts")).hasSize(1);
        assertThat(JsonPath.<String>read(body, "$.accounts[0].maskedEmail")).isEqualTo("ab***@gmail.com");
        assertThat(JsonPath.<String>read(body, "$.accounts[0].joinedOn")).isEqualTo("2026-10-02");
        assertThat(JsonPath.<String>read(body, "$.accounts[0].resetToken")).isNotBlank();
        assertThat(body).doesNotContain("abcdef");
        // 토큰은 해시로만 보관한다
        String token = JsonPath.read(body, "$.accounts[0].resetToken");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens", String.class))
                .isNotEqualTo(token);
    }

    @Test
    void 시나리오2_탈퇴한_계정은_빼고_여러_개면_모두_보여준다() throws Exception {
        user("first@x.com", "첫째", "2026-10-01");
        long gone = user("gone@x.com", "탈퇴", "2026-10-02");
        user("third@x.com", "셋째", "2026-10-03");
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_email = email, email = NULL, withdrawn_at = ? "
                + "WHERE id = ?", BASE_TIME, gone);
        String body = findBody("홍길동", "01012345678");
        assertThat(JsonPath.<List<String>>read(body, "$.accounts[*].maskedEmail"))
                .containsExactly("fi***@x.com", "th***@x.com");
        assertThat(body).doesNotContain("first@").doesNotContain("third@").doesNotContain("gone");
    }

    @Test
    void 시나리오3_일치하는_계정이_없으면_빈_목록() throws Exception {
        user("me@x.com", "나닉", "2026-10-02");
        find("김철수", "01012345678").andExpect(jsonPath("$.accounts").isEmpty());
        find("홍길동", "01099999999").andExpect(jsonPath("$.accounts").isEmpty());
        find("홍길동", "abc").andExpect(status().isOk()).andExpect(jsonPath("$.accounts").isEmpty());
    }

    @Test
    void 시나리오4_앞이_2글자_이하면_첫_글자만() throws Exception {
        user("ab@x.com", "짧은", "2026-10-02");
        String body = findBody("홍길동", "01012345678");
        assertThat(JsonPath.<String>read(body, "$.accounts[0].maskedEmail")).isEqualTo("a***@x.com");
        assertThat(body).doesNotContain("ab@x.com");
    }

    @Test
    void 시나리오5_재설정을_누르면_그_이메일로_번호가_가고_같은_절차로_바꾼다() throws Exception {
        user("me@x.com", "나닉", "2026-10-02");
        String token = JsonPath.read(findBody("홍길동", "01012345678"), "$.accounts[0].resetToken");
        resetCode("{\"resetToken\":\"" + token + "\"}").andExpect(status().isAccepted());
        awaitMails(1);
        String code = codeIn(lastMailTo("me@x.com"));

        mvc.perform(postJson("/api/auth/password-reset/verify",
                        "{\"resetToken\":\"%s\",\"code\":\"%s\"}".formatted(token, code)))
                .andExpect(status().isOk());
        mvc.perform(postJson("/api/auth/password-reset",
                        "{\"resetToken\":\"%s\",\"code\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirm\":\"%s\"}"
                                .formatted(token, code, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());
        loginAs("me@x.com", NEW_PASSWORD, false);
        // 쓴 토큰은 바로 지운다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class)).isZero();
        resetCode("{\"resetToken\":\"" + token + "\"}").andExpect(status().isGone());
    }

    @Test
    void 시나리오6_10분이_지난_토큰은_410이고_회원_번호를_넣어도_토큰의_계정에만_메일() throws Exception {
        user("me@x.com", "나닉", "2026-10-02");
        long other = user("other@y.com", "남", "2026-10-02");
        jdbc.update("UPDATE users SET name = '김철수' WHERE id = ?", other);
        String token = JsonPath.read(findBody("홍길동", "01012345678"), "$.accounts[0].resetToken");

        resetCode("{\"resetToken\":\"" + token + "\",\"memberId\":" + other + ",\"userId\":" + other + "}")
                .andExpect(status().isAccepted());
        awaitMails(1);
        assertThat(mailsAfterQuietPeriod()).hasSize(1);
        assertThat(lastMailTo("me@x.com")).isNotNull();

        clock.advance(Duration.ofMinutes(10));
        resetCode("{\"resetToken\":\"" + token + "\"}").andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        mvc.perform(postJson("/api/auth/password-reset/verify",
                        "{\"resetToken\":\"%s\",\"code\":\"123456\"}".formatted(token)))
                .andExpect(status().isGone());
        resetCode("{\"resetToken\":\"made-up-token\"}").andExpect(status().isGone());
    }

    @Test
    void 시나리오7_SC007_같은_IP의_6번째는_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            find("홍길동", "01012345678").andExpect(status().isOk());
        }
        find("홍길동", "01012345678").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        // 다른 IP는 영향이 없다
        ip = nextIp();
        find("홍길동", "01012345678").andExpect(status().isOk());
    }
}
