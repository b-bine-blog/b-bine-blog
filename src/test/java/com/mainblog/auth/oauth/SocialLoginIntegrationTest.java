package com.mainblog.auth.oauth;

import static com.mainblog.support.AuthTestSupport.cookieValue;
import static com.mainblog.support.AuthTestSupport.postJson;
import static com.mainblog.support.AuthTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mainblog.auth.oauth.SocialLoginService.CallbackResult;
import com.mainblog.auth.oauth.SocialLoginService.Outcome;
import com.mainblog.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 소셜 로그인 (008 US1~US4, SC-001~SC-006, D-125).
 * 소셜 서비스에 실제로 다녀올 수는 없어서, 돌아온 뒤의 처리(onCallback)부터 API로 확인한다.
 * 테스트 설정은 구글 키만 넣어 둔다(application-test.yml).
 */
class SocialLoginIntegrationTest extends IntegrationTestBase {

    static final String PASSWORD = "Abcd123!";

    @Autowired
    SocialLoginService service;

    private String pendingToken(SocialProfile profile) {
        CallbackResult result = service.onCallback(profile, false, false, null);
        assertThat(result.outcome()).isEqualTo(Outcome.PENDING);
        return result.pendingToken();
    }

    private static String signupBody(String token, String nickname) {
        return """
                {"token":"%s","name":"홍길동","nickname":"%s","phone":"010-1234-5678",
                 "agreeTerms":true,"agreePrivacy":true}""".formatted(token, nickname);
    }

    private LoginCookies signUpSocial(SocialProfile profile, String nickname) throws Exception {
        String token = pendingToken(profile);
        MockHttpServletResponse res = mvc.perform(postJson("/api/social/signup", signupBody(token, nickname)))
                .andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return new LoginCookies(cookieValue(res, "AT"), cookieValue(res, "RT"));
    }

    @Test
    void 키가_있는_서비스만_버튼_목록에_나온다() throws Exception {
        mvc.perform(get("/api/social/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(1))
                .andExpect(jsonPath("$.providers[0]").value("google"));
    }

    @Test
    void 구글_로그인을_누르면_PKCE와_함께_구글로_가고_요청은_서명된_쿠키에_둔다() throws Exception {
        MockHttpServletResponse res = mvc.perform(get("/oauth2/authorization/google").param("remember", "1"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        String location = res.getHeader("Location");
        assertThat(location).startsWith("https://accounts.google.com/")
                .contains("code_challenge=", "state=", "client_id=test-google-client")
                .contains("redirect_uri=http://localhost:8080/login/oauth2/code/google");
        String cookie = res.getHeaders("Set-Cookie").stream().filter(h -> h.startsWith("OAUTH2_REQ="))
                .findFirst().orElseThrow();
        assertThat(cookie).contains("HttpOnly");
        // 세션을 만들지 않는다(STATELESS)
        assertThat(res.getHeaders("Set-Cookie")).noneMatch(h -> h.startsWith("JSESSIONID="));
    }

    @Test
    void 키가_없는_서비스로는_로그인을_시작할_수_없다() throws Exception {
        int status = mvc.perform(get("/oauth2/authorization/kakao")).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(301, 302, 303, 307);
    }

    @Test
    void US1_확인된_이메일이면_회원정보만_적고_비밀번호_없이_가입한다() throws Exception {
        SocialProfile profile = new SocialProfile(SocialProvider.GOOGLE, "g-1", "new@x.com");
        String token = pendingToken(profile);
        mvc.perform(postJson("/api/social/pending", "{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.step").value("SIGNUP"))
                .andExpect(jsonPath("$.maskedEmail").value(org.hamcrest.Matchers.not("new@x.com")));

        MockHttpServletResponse res = mvc.perform(postJson("/api/social/signup", signupBody(token, "소셜닉")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(cookieValue(res, "AT")).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT password_hash IS NULL FROM users WHERE email = 'new@x.com'",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_accounts WHERE provider = 'GOOGLE' "
                + "AND provider_user_id = 'g-1'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_pendings", Integer.class)).isZero();

        // 다음부터는 바로 로그인된다(US2)
        CallbackResult again = service.onCallback(profile, true, false, null);
        assertThat(again.outcome()).isEqualTo(Outcome.LOGGED_IN);
        assertThat(again.tokens().rememberMe()).isTrue();

        // 같은 대기 토큰은 다시 쓸 수 없다
        mvc.perform(postJson("/api/social/signup", signupBody(token, "다른닉")))
                .andExpect(status().isGone());
    }

    @Test
    void US1_이메일이_없으면_인증번호로_확인한_뒤_가입한다() throws Exception {
        String token = pendingToken(new SocialProfile(SocialProvider.GOOGLE, "g-2", null));
        mvc.perform(postJson("/api/social/pending", "{\"token\":\"" + token + "\"}"))
                .andExpect(jsonPath("$.step").value("EMAIL"));
        mvc.perform(postJson("/api/social/email-code", "{\"token\":\"" + token + "\",\"email\":\"mail@x.com\"}"))
                .andExpect(status().isNoContent());
        String code = codeIn(awaitMails(1)[0]);
        mvc.perform(postJson("/api/social/email-verify",
                        "{\"token\":\"" + token + "\",\"email\":\"mail@x.com\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("SIGNUP"));
        mvc.perform(postJson("/api/social/signup", signupBody(token, "메일닉"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'mail@x.com'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void US3_같은_이메일_회원은_비밀번호를_확인해야_연결된다() throws Exception {
        long id = createUser("same@x.com", PASSWORD, "기존닉");
        String token = pendingToken(new SocialProfile(SocialProvider.GOOGLE, "g-3", "same@x.com"));
        mvc.perform(postJson("/api/social/pending", "{\"token\":\"" + token + "\"}"))
                .andExpect(jsonPath("$.step").value("LINK"));

        mvc.perform(postJson("/api/social/link", "{\"token\":\"" + token + "\",\"password\":\"Wrong123!\"}")
                        .with(r -> { r.setRemoteAddr(nextIp()); return r; }))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_accounts", Integer.class)).isZero();

        MockHttpServletResponse res = mvc.perform(postJson("/api/social/link",
                        "{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}")
                        .with(r -> { r.setRemoteAddr(nextIp()); return r; }))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(cookieValue(res, "AT")).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT user_id FROM social_accounts WHERE provider_user_id = 'g-3'",
                Long.class)).isEqualTo(id);
    }

    @Test
    void 대기_정보는_시간이_지나면_쓸_수_없다() throws Exception {
        String token = pendingToken(new SocialProfile(SocialProvider.GOOGLE, "g-4", "late@x.com"));
        clock.advance(Duration.ofMinutes(31));
        mvc.perform(postJson("/api/social/pending", "{\"token\":\"" + token + "\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SOCIAL_PENDING_INVALID"));
    }

    @Test
    void US4_내_정보에서_연결하고_마지막_로그인_방법은_끊지_못한다() throws Exception {
        LoginCookies login = signUpSocial(new SocialProfile(SocialProvider.GOOGLE, "g-5", "only@x.com"), "혼자닉");
        mvc.perform(get("/api/members/me/social").cookie(login.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.accounts[0].provider").value("google"))
                .andExpect(jsonPath("$.available[0]").value("google"));

        mvc.perform(withCsrf(delete("/api/members/me/social/google")).cookie(login.cookies()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOCIAL_LAST_LOGIN_METHOD"));

        // 비밀번호를 만들면(현재 비밀번호 없이) 끊을 수 있다
        mvc.perform(withCsrf(put("/api/members/me/password")).cookie(login.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + PASSWORD + "\",\"newPasswordConfirm\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        LoginCookies relogin = loginAs("only@x.com", PASSWORD, false);
        mvc.perform(withCsrf(delete("/api/members/me/social/google")).cookie(relogin.cookies()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_accounts", Integer.class)).isZero();
    }

    @Test
    void US4_다른_회원에게_연결된_소셜은_연결할_수_없다() throws Exception {
        signUpSocial(new SocialProfile(SocialProvider.GOOGLE, "g-6", "first@x.com"), "첫째닉");
        long other = createUser("other@x.com", PASSWORD, "둘째닉");
        CallbackResult result = service.onCallback(new SocialProfile(SocialProvider.GOOGLE, "g-6", null),
                false, true, other);
        assertThat(result.outcome()).isEqualTo(Outcome.ERROR);
        assertThat(result.error().name()).isEqualTo("SOCIAL_ALREADY_LINKED");
    }

    @Test
    void 비밀번호_없는_소셜_회원은_비밀번호_없이_탈퇴하고_연결도_지워진다() throws Exception {
        LoginCookies login = signUpSocial(new SocialProfile(SocialProvider.GOOGLE, "g-7", "bye@x.com"), "탈퇴닉");
        mvc.perform(withCsrf(post("/api/members/me/withdrawal")).cookie(login.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_accounts", Integer.class)).isZero();
        // 같은 소셜로 다시 오면 새 가입으로 간다
        assertThat(service.onCallback(new SocialProfile(SocialProvider.GOOGLE, "g-7", "bye@x.com"), false, false,
                null).outcome()).isEqualTo(Outcome.PENDING);
    }

    @Test
    void 비밀번호가_있는_회원은_탈퇴할_때_여전히_비밀번호가_필요하다() throws Exception {
        createUser("pw@x.com", PASSWORD, "비번닉");
        LoginCookies login = loginAs("pw@x.com", PASSWORD, false);
        mvc.perform(withCsrf(post("/api/members/me/withdrawal")).cookie(login.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 비밀번호_없는_회원은_이메일로_로그인할_수_없다() throws Exception {
        signUpSocial(new SocialProfile(SocialProvider.GOOGLE, "g-8", "nopw@x.com"), "무비닉");
        mvc.perform(postJson("/api/auth/login", "{\"email\":\"nopw@x.com\",\"password\":\"Abcd123!\","
                        + "\"rememberMe\":false}").with(r -> { r.setRemoteAddr(nextIp()); return r; }))
                .andExpect(status().isUnauthorized());
    }
}
