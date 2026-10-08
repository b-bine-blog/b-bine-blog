package com.mainblog.perf;

import static com.mainblog.support.AuthTestSupport.loginJson;
import static com.mainblog.support.AuthTestSupport.postJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.support.IntegrationTestBase;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 계정 화면 응답 시간 (T073, SC-010, R-08, D-28).
 * 메일은 비동기로 보내므로 발송 API는 가입 여부와 관계없이 비슷한 시간에 답해야 한다.
 */
class AuthResponseTimeTest extends IntegrationTestBase {

    static final long LIMIT_MS = 2000;
    static final int ROUNDS = 7;
    /** 가입된·안 된 이메일 응답 시간 중앙값 차이 허용치(측정 오차). */
    static final long TIMING_TOLERANCE_MS = 150;

    @BeforeEach
    void setUp() {
        createUser("user@x.com", "Abcd123!", "회원닉");
    }

    private long elapsedMs(MockHttpServletRequestBuilder request) throws Exception {
        String ip = nextIp();
        long start = System.nanoTime();
        int status = mvc.perform(request.with(r -> { r.setRemoteAddr(ip); return r; }))
                .andReturn().getResponse().getStatus();
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(status).isLessThan(500);
        return ms;
    }

    private long median(MockHttpServletRequestBuilder request) throws Exception {
        return median(i -> request);
    }

    /** 회차마다 다른 요청(로그인 실패 횟수가 쌓여 사람 확인 단계로 넘어가지 않게). */
    private long median(java.util.function.IntFunction<MockHttpServletRequestBuilder> request) throws Exception {
        long[] samples = new long[ROUNDS];
        for (int i = 0; i < ROUNDS; i++) {
            samples[i] = elapsedMs(request.apply(i));
        }
        Arrays.sort(samples);
        return samples[ROUNDS / 2];
    }

    @Test
    void 로그인_가입발송_비밀번호찾기_이메일찾기가_2초_안에_답한다() throws Exception {
        // 첫 요청의 클래스 로딩·JIT 시간을 빼려고 한 번씩 먼저 보낸다
        elapsedMs(postJson("/api/auth/login", loginJson("user@x.com", "Abcd123!", false, null)));

        assertThat(elapsedMs(postJson("/api/auth/login", loginJson("user@x.com", "Abcd123!", false, null))))
                .isLessThan(LIMIT_MS);
        assertThat(elapsedMs(postJson("/api/auth/signup/email-code", "{\"email\":\"new@x.com\"}")))
                .isLessThan(LIMIT_MS);
        assertThat(elapsedMs(postJson("/api/auth/password-reset/code", "{\"email\":\"user@x.com\"}")))
                .isLessThan(LIMIT_MS);
        assertThat(elapsedMs(postJson("/api/auth/find-email", "{\"name\":\"홍길동\",\"phone\":\"01012345678\"}")))
                .isLessThan(LIMIT_MS);
    }

    @Test
    void 가입된_이메일과_안_된_이메일의_발송_응답_시간이_비슷하다() throws Exception {
        median(postJson("/api/auth/password-reset/code", "{\"email\":\"warmup@x.com\"}"));

        long knownReset = median(postJson("/api/auth/password-reset/code", "{\"email\":\"user@x.com\"}"));
        long unknownReset = median(postJson("/api/auth/password-reset/code", "{\"email\":\"nobody@x.com\"}"));
        assertThat(Math.abs(knownReset - unknownReset)).isLessThanOrEqualTo(TIMING_TOLERANCE_MS);

        long knownSignup = median(postJson("/api/auth/signup/email-code", "{\"email\":\"user@x.com\"}"));
        long unknownSignup = median(postJson("/api/auth/signup/email-code", "{\"email\":\"fresh@x.com\"}"));
        assertThat(Math.abs(knownSignup - unknownSignup)).isLessThanOrEqualTo(TIMING_TOLERANCE_MS);

        // 로그인: 없는 이메일도 더미 해시와 비교해 시간이 비슷하다(FR-021)
        for (int i = 0; i < ROUNDS; i++) {
            createUser("known" + i + "@x.com", "Abcd123!", "회원" + i);
        }
        long knownLogin = median(i -> postJson("/api/auth/login",
                loginJson("known" + i + "@x.com", "Wrong123!", false, null)));
        long unknownLogin = median(i -> postJson("/api/auth/login",
                loginJson("nobody" + i + "@x.com", "Wrong123!", false, null)));
        assertThat(Math.abs(knownLogin - unknownLogin)).isLessThanOrEqualTo(TIMING_TOLERANCE_MS);
    }
}
