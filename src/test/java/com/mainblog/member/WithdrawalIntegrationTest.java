package com.mainblog.member;

import static com.mainblog.support.AuthTestSupport.postJson;
import static com.mainblog.support.AuthTestSupport.setCookieHeader;
import static com.mainblog.support.AuthTestSupport.signupJson;
import static com.mainblog.support.AuthTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mainblog.batch.DailyCleanupJob;
import com.mainblog.support.IntegrationTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/** 회원탈퇴 (T045, US3 인수 시나리오 1~6, SC-008). */
class WithdrawalIntegrationTest extends IntegrationTestBase {

    static final String PASSWORD = "Abcd123!";

    private MockHttpServletResponse withdraw(LoginCookies login, String password) throws Exception {
        return mvc.perform(withCsrf(post("/api/members/me/withdrawal")).cookie(login.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + password + "\"}"))
                .andReturn().getResponse();
    }

    @Test
    void 시나리오1_블로그장이_아니면_탈퇴되고_로그인할_수_없다() throws Exception {
        long id = createUser("me@x.com", PASSWORD, "나닉");
        LoginCookies login = loginAs("me@x.com", PASSWORD, false);
        mvc.perform(get("/api/members/me/withdrawal").cookie(login.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canWithdraw").value(true))
                .andExpect(jsonPath("$.ownedBlogs").isEmpty());

        MockHttpServletResponse res = withdraw(login, PASSWORD);
        assertThat(res.getStatus()).isEqualTo(204);
        assertThat(setCookieHeader(res, "RT")).contains("Max-Age=0");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT email, withdrawn_email, nickname, status, withdrawn_at FROM users WHERE id = ?", id);
        assertThat(row.get("email")).isNull();
        assertThat(row).containsEntry("withdrawn_email", "me@x.com").containsEntry("nickname", "나닉")
                .containsEntry("status", "WITHDRAWN");
        assertThat(row.get("withdrawn_at")).isNotNull();

        mvc.perform(postJson("/api/auth/login", "{\"email\":\"me@x.com\",\"password\":\"" + PASSWORD
                        + "\",\"rememberMe\":false}").with(r -> { r.setRemoteAddr(nextIp()); return r; }))
                .andExpect(status().isUnauthorized());
        // 기존 RT로도 다시 로그인 상태를 만들 수 없다
        mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(new Cookie("RT", login.rt())))
                .andExpect(status().isUnauthorized());
        // 기존 AT도 바로 막힌다(요청마다 ACTIVE 확인)
        mvc.perform(get("/api/auth/session").cookie(new Cookie("AT", login.at())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 시나리오2_비밀번호가_틀리면_403_PASSWORD_MISMATCH() throws Exception {
        createUser("me@x.com", PASSWORD, "나닉");
        LoginCookies login = loginAs("me@x.com", PASSWORD, false);
        MockHttpServletResponse res = withdraw(login, "Wrong123!");
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("PASSWORD_MISMATCH");
        assertThat(jdbc.queryForObject("SELECT status FROM users", String.class)).isEqualTo("ACTIVE");
    }

    @Test
    void 시나리오3_SC008_운영_중인_블로그가_있으면_목록이_보이고_409() throws Exception {
        long id = createUser("owner@x.com", PASSWORD, "블로그장");
        createOwnedBlog(id, "first", "ACTIVE");
        createOwnedBlog(id, "second", "ACTIVE");
        LoginCookies login = loginAs("owner@x.com", PASSWORD, false);

        mvc.perform(get("/api/members/me/withdrawal").cookie(login.cookies()))
                .andExpect(jsonPath("$.canWithdraw").value(false))
                .andExpect(jsonPath("$.ownedBlogs.length()").value(2))
                .andExpect(jsonPath("$.ownedBlogs[0].address").value("first"))
                .andExpect(jsonPath("$.ownedBlogs[0].status").value("ACTIVE"));
        MockHttpServletResponse res = withdraw(login, PASSWORD);
        assertThat(res.getStatus()).isEqualTo(409);
        assertThat(res.getContentAsString()).contains("OWNS_BLOGS");
    }

    @Test
    void 시나리오4_SC008_폐쇄_예정_블로그가_있어도_409() throws Exception {
        long id = createUser("owner@x.com", PASSWORD, "블로그장");
        createOwnedBlog(id, "closing", "CLOSING");
        LoginCookies login = loginAs("owner@x.com", PASSWORD, false);
        mvc.perform(get("/api/members/me/withdrawal").cookie(login.cookies()))
                .andExpect(jsonPath("$.ownedBlogs[0].status").value("CLOSING"))
                .andExpect(jsonPath("$.ownedBlogs[0].closeScheduledAt").value("2026-10-15T04:00:00+09:00"));
        assertThat(withdraw(login, PASSWORD).getStatus()).isEqualTo(409);
    }

    @Autowired
    DailyCleanupJob dailyCleanupJob;

    /** 002 T052, FR-080: API로 만든 블로그를 폐쇄 예약하면 탈퇴가 막히고, 04:00에 폐쇄된 뒤에는 탈퇴된다. */
    @Test
    void 실제_블로그를_폐쇄_예약하면_폐쇄가_끝날_때까지_탈퇴할_수_없다() throws Exception {
        createUser("owner@x.com", PASSWORD, "블로그장");
        LoginCookies login = loginAs("owner@x.com", PASSWORD, false);
        String created = mvc.perform(withCsrf(post("/api/blogs")).cookie(login.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"탈퇴\",\"address\":\"bye-blog\",\"visibility\":\"PUBLIC\","
                                + "\"joinPolicy\":\"FREE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long blogId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(withCsrf(post("/api/blogs/" + blogId + "/closing")).cookie(login.cookies()))
                .andExpect(status().isOk());
        assertThat(withdraw(login, PASSWORD).getStatus()).isEqualTo(409);

        clock.advance(java.time.Duration.ofDays(8));
        dailyCleanupJob.runOnce();
        assertThat(jdbc.queryForObject("SELECT status FROM blogs WHERE id = ?", String.class, blogId))
                .isEqualTo("CLOSED");
        LoginCookies again = loginAs("owner@x.com", PASSWORD, false);
        assertThat(withdraw(again, PASSWORD).getStatus()).isEqualTo(204);
    }

    @Test
    void 폐쇄된_블로그만_있으면_탈퇴할_수_있다() throws Exception {
        long id = createUser("owner@x.com", PASSWORD, "블로그장");
        createOwnedBlog(id, "closed", "CLOSED");
        LoginCookies login = loginAs("owner@x.com", PASSWORD, false);
        assertThat(withdraw(login, PASSWORD).getStatus()).isEqualTo(204);
    }

    @Test
    void 시나리오5_SC008_탈퇴_직후_같은_이메일로_재가입되고_이전_닉네임은_30일_동안_막힌다() throws Exception {
        createUser("me@x.com", PASSWORD, "나닉");
        assertThat(withdraw(loginAs("me@x.com", PASSWORD, false), PASSWORD).getStatus()).isEqualTo(204);

        mvc.perform(postJson("/api/auth/signup/email-code", "{\"email\":\"me@x.com\"}"))
                .andExpect(status().isAccepted());
        String code = codeIn(awaitMails(1)[0]);
        mvc.perform(postJson("/api/auth/signup/email-code/verify", "{\"email\":\"me@x.com\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(postJson("/api/auth/signup", signupJson("me@x.com", "나닉")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATE"));
        mvc.perform(postJson("/api/auth/signup", signupJson("me@x.com", "새닉")))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'me@x.com' AND status='ACTIVE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/members/me/withdrawal")).andExpect(status().isUnauthorized());
    }
}
