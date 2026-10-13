package com.mainblog.auth;

import static com.mainblog.support.AuthTestSupport.postJson;
import static com.mainblog.support.AuthTestSupport.signupJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mainblog.support.IntegrationTestBase;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;

/** 가입 (T025, US1 인수 시나리오 1~5, 9~11, SC-001, SC-002, SC-009, FR-007). */
class SignupIntegrationTest extends IntegrationTestBase {

    @Autowired
    PasswordEncoder passwordEncoder;

    private MockHttpServletResponse sendCode(String email) throws Exception {
        return mvc.perform(postJson("/api/auth/signup/email-code", "{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse();
    }

    private void verify(String email, String code) throws Exception {
        mvc.perform(postJson("/api/auth/signup/email-code/verify",
                        "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedUntil").exists());
    }

    private void verifiedEmail(String email, int mailsSoFar) throws Exception {
        sendCode(email);
        awaitMails(mailsSoFar + 1);
        verify(email, codeIn(lastMailTo(email)));
    }

    private void insertUser(String email, String nickname) {
        jdbc.update("INSERT INTO users (email, password_hash, name, nickname, phone, role, status) "
                + "VALUES (?, ?, '기존회원', ?, '01099998888', 'USER', 'ACTIVE')",
                email, passwordEncoder.encode("Abcd123!"), nickname);
    }

    @Test
    void 시나리오1_새_이메일이면_6자리_인증번호가_간다() throws Exception {
        sendCode("new@x.com");
        MimeMessage mail = awaitMails(1)[0];
        assertThat(codeIn(mail)).matches("\\d{6}");
        assertThat(mail.getSubject()).contains("인증번호");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email='new@x.com'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 시나리오2_SC001_가입된_이메일도_응답이_같고_안내_메일만_간다() throws Exception {
        insertUser("old@x.com", "기존닉");
        MockHttpServletResponse registered = sendCode("old@x.com");
        MockHttpServletResponse fresh = sendCode("fresh@x.com");

        assertThat(registered.getStatus()).isEqualTo(fresh.getStatus());
        assertThat(registered.getContentAsString()).isEqualTo(fresh.getContentAsString());
        assertThat(registered.getHeaderNames()).containsExactlyInAnyOrderElementsOf(fresh.getHeaderNames());

        awaitMails(2);
        MimeMessage toOld = lastMailTo("old@x.com");
        assertThat(toOld.getSubject()).contains("이미 가입된 이메일");
        assertThat(String.valueOf(toOld.getContent())).doesNotContainPattern("\\d{6}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email='old@x.com'",
                Integer.class)).isZero();
    }

    @Test
    void 시나리오3_SC002_10분이_지난_번호는_거부() throws Exception {
        sendCode("new@x.com");
        String code = codeIn(awaitMails(1)[0]);
        clock.advance(Duration.ofMinutes(10));
        mvc.perform(postJson("/api/auth/signup/email-code/verify",
                        "{\"email\":\"new@x.com\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_INVALID"));
    }

    @Test
    void 시나리오4_SC002_5번째_오류면_맞는_번호도_통과하지_않는다() throws Exception {
        sendCode("new@x.com");
        String code = codeIn(awaitMails(1)[0]);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 4; i++) {
            mvc.perform(postJson("/api/auth/signup/email-code/verify",
                            "{\"email\":\"new@x.com\",\"code\":\"" + wrong + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CODE_MISMATCH"))
                    .andExpect(jsonPath("$.remainingAttempts").value(5 - i));
        }
        mvc.perform(postJson("/api/auth/signup/email-code/verify",
                        "{\"email\":\"new@x.com\",\"code\":\"" + wrong + "\"}"))
                .andExpect(jsonPath("$.code").value("CODE_INVALID"));
        mvc.perform(postJson("/api/auth/signup/email-code/verify",
                        "{\"email\":\"new@x.com\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_INVALID"));
    }

    @Test
    void 시나리오5_SC002_1분_안에는_재발송_안되고_1분_뒤_새_번호는_이전_번호를_무효로() throws Exception {
        sendCode("new@x.com");
        String first = codeIn(awaitMails(1)[0]);
        clock.advance(Duration.ofSeconds(59));
        sendCode("new@x.com");
        assertThat(mailsAfterQuietPeriod()).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        sendCode("new@x.com");
        String second = codeIn(awaitMails(2)[1]);
        if (!first.equals(second)) {
            mvc.perform(postJson("/api/auth/signup/email-code/verify",
                            "{\"email\":\"new@x.com\",\"code\":\"" + first + "\"}"))
                    .andExpect(jsonPath("$.code").value("CODE_MISMATCH"));
        }
        verify("new@x.com", second);
    }

    @Test
    void 끝까지_진행하면_계정과_동의_2건이_생기고_인증_행은_지워진다() throws Exception {
        verifiedEmail("New@X.com", 0);
        mvc.perform(postJson("/api/auth/signup", signupJson("NEW@x.com", "새회원")))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForMap("SELECT email, name, nickname, phone, role, status FROM users"))
                .containsEntry("email", "new@x.com")
                .containsEntry("name", "홍길동")
                .containsEntry("nickname", "새회원")
                .containsEntry("phone", "01012345678")
                .containsEntry("role", "USER")
                .containsEntry("status", "ACTIVE");
        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).startsWith("$2a$10$");
        assertThat(jdbc.queryForList("SELECT agreement_type FROM user_agreements ORDER BY agreement_type",
                String.class)).containsExactly("PRIVACY", "TERMS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_agreements WHERE agreed_at = ?",
                Integer.class, BASE_TIME)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications", Integer.class)).isZero();
    }

    @Test
    void 시나리오8_같은_전화번호가_있어도_가입된다() throws Exception {
        jdbc.update("INSERT INTO users (email, password_hash, name, nickname, phone) "
                + "VALUES ('other@x.com', 'h', '다른이', '다른이', '01012345678')");
        verifiedEmail("new@x.com", 0);
        mvc.perform(postJson("/api/auth/signup", signupJson("new@x.com", "새회원")))
                .andExpect(status().isCreated());
    }

    @Test
    void 시나리오9_필수_동의가_하나라도_없으면_가입되지_않는다() throws Exception {
        verifiedEmail("new@x.com", 0);
        String noPrivacy = signupJson("new@x.com", "새회원").replace("\"agreePrivacy\":true", "\"agreePrivacy\":false");
        mvc.perform(postJson("/api/auth/signup", noPrivacy))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("agreePrivacy"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void 시나리오10_인증을_건너뛰면_403_EMAIL_NOT_VERIFIED() throws Exception {
        mvc.perform(postJson("/api/auth/signup", signupJson("skip@x.com", "건너뜀")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        // 번호만 받고 확인하지 않은 경우도 같다
        sendCode("skip@x.com");
        awaitMails(1);
        mvc.perform(postJson("/api/auth/signup", signupJson("skip@x.com", "건너뜀")))
                .andExpect(status().isForbidden());
    }

    @Test
    void FR007_인증_뒤_31분이면_403() throws Exception {
        verifiedEmail("late@x.com", 0);
        clock.advance(Duration.ofMinutes(31));
        mvc.perform(postJson("/api/auth/signup", signupJson("late@x.com", "늦은이")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void 시나리오11_대소문자만_다른_이메일은_계정이_생기지_않는다() throws Exception {
        verifiedEmail("a@x.com", 0);
        // 인증 중에 같은 이메일 가입이 먼저 끝났다
        insertUser("a@x.com", "먼저");
        mvc.perform(postJson("/api/auth/signup", signupJson("A@x.com", "나중")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNUP_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);

        // ①단계는 안내 메일만 보낸다
        sendCode("A@X.COM");
        awaitMails(2);
        assertThat(lastMailTo("a@x.com").getSubject()).contains("이미 가입된 이메일");
    }

    @Test
    void 중복_닉네임은_409_NICKNAME_DUPLICATE_대소문자_무시() throws Exception {
        insertUser("old@x.com", "ABC");
        verifiedEmail("new@x.com", 0);
        mvc.perform(postJson("/api/auth/signup", signupJson("new@x.com", "abc")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATE"));
    }

    @Test
    void 예약어와_규칙에_맞지_않는_비밀번호는_400() throws Exception {
        verifiedEmail("new@x.com", 0);
        mvc.perform(postJson("/api/auth/signup", signupJson("new@x.com", "admin1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nickname"));
        mvc.perform(postJson("/api/auth/signup",
                        signupJson("new@x.com", "새회원").replace("Abcd123!", "abcd1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    void SC009_같은_닉네임으로_동시에_가입하면_하나만_생긴다() throws Exception {
        verifiedEmail("one@x.com", 0);
        verifiedEmail("two@x.com", 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String email : List.of("one@x.com", "two@x.com")) {
            Callable<Integer> call = () -> {
                start.await();
                return mvc.perform(postJson("/api/auth/signup", signupJson(email, "같은닉")))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE nickname='같은닉'", Integer.class))
                .isEqualTo(1);
    }
}
