package com.mainblog.member;

import static com.mainblog.support.AuthTestSupport.cookieValue;
import static com.mainblog.support.AuthTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mainblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/** 내 정보 보기와 회원정보 수정 (T057, US5 인수 시나리오 1~4, 6). */
class MyInfoIntegrationTest extends IntegrationTestBase {

    static final String PASSWORD = "Abcd123!";
    static final String NEW_PASSWORD = "Newpw456@";

    private ResultActions patchMe(LoginCookies login, String json) throws Exception {
        return mvc.perform(withCsrf(patch("/api/members/me")).cookie(login.cookies())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private MockHttpServletResponse changePassword(LoginCookies login, String current, String next, String confirm)
            throws Exception {
        return mvc.perform(withCsrf(put("/api/members/me/password")).cookie(login.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirm\":\"%s\"}"
                                .formatted(current, next, confirm)))
                .andReturn().getResponse();
    }

    @Test
    void 시나리오1_내_정보는_가리지_않고_비밀번호_필드가_없다() throws Exception {
        createUser("me@x.com", PASSWORD, "나닉");
        LoginCookies login = loginAs("me@x.com", PASSWORD, false);
        String body = mvc.perform(get("/api/members/me").cookie(login.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@x.com"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.nickname").value("나닉"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.joinedOn").value("2026-10-08"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("password");
        mvc.perform(get("/api/members/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void 시나리오2_닉네임_중복_예약어_형식은_거부되고_규칙에_맞으면_바뀐다() throws Exception {
        createUser("other@x.com", PASSWORD, "남의닉");
        createUser("me@x.com", PASSWORD, "나닉");
        LoginCookies login = loginAs("me@x.com", PASSWORD, false);

        patchMe(login, "{\"nickname\":\"남의닉\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATE"));
        patchMe(login, "{\"nickname\":\"ADMIN계정\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("Reserved"));
        patchMe(login, "{\"nickname\":\"a\"}").andExpect(status().isBadRequest());
        patchMe(login, "{\"phone\":\"02-123-4567\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phone"));

        patchMe(login, "{\"nickname\":\"새닉네임\",\"phone\":\"010-9999-8888\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.phone").value("01099998888"))
                .andExpect(jsonPath("$.email").value("me@x.com"));
        // 자기 닉네임의 대소문자만 바꾸는 것은 중복이 아니다
        patchMe(login, "{\"nickname\":\"Abc1\"}").andExpect(status().isOk());
        patchMe(login, "{\"nickname\":\"abc1\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("abc1"));
    }

    @Test
    void 시나리오3_이메일이나_이름을_보내면_400이고_바뀌지_않는다() throws Exception {
        createUser("me@x.com", PASSWORD, "나닉");
        LoginCookies login = loginAs("me@x.com", PASSWORD, false);
        patchMe(login, "{\"email\":\"new@x.com\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
        patchMe(login, "{\"nickname\":\"새닉\",\"name\":\"김철수\"}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForMap("SELECT email, name, nickname FROM users"))
                .containsEntry("email", "me@x.com").containsEntry("name", "홍길동").containsEntry("nickname", "나닉");
    }

    @Test
    void 시나리오4_현재_비밀번호가_틀리면_403_맞으면_바뀌고_다른_기기만_로그아웃() throws Exception {
        long id = createUser("me@x.com", PASSWORD, "나닉");
        LoginCookies here = loginAs("me@x.com", PASSWORD, true);
        LoginCookies other = loginAs("me@x.com", PASSWORD, false);

        MockHttpServletResponse wrong = changePassword(here, "Wrong123!", NEW_PASSWORD, NEW_PASSWORD);
        assertThat(wrong.getStatus()).isEqualTo(403);
        assertThat(wrong.getContentAsString()).contains("CURRENT_PASSWORD_MISMATCH");
        assertThat(changePassword(here, PASSWORD, "short1!", "short1!").getStatus()).isEqualTo(400);
        assertThat(changePassword(here, PASSWORD, NEW_PASSWORD, "Newpw456#").getStatus()).isEqualTo(400);
        assertThat(passwordEncoder.matches(PASSWORD,
                jdbc.queryForObject("SELECT password_hash FROM users", String.class))).isTrue();

        MockHttpServletResponse ok = changePassword(here, PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
        assertThat(ok.getStatus()).isEqualTo(204);
        String newRt = cookieValue(ok, "RT");
        String newAt = cookieValue(ok, "AT");
        assertThat(newRt).isNotBlank().isNotEqualTo(here.rt());
        assertThat(newAt).isNotBlank();
        // 로그인 유지(체크) 기기였으므로 새 RT도 Max-Age가 있다
        assertThat(ok.getHeaders("Set-Cookie").stream().filter(h -> h.startsWith("RT=")).findFirst().orElseThrow())
                .contains("Max-Age=");

        // 이 기기: 새 RT로 refresh 성공, 예전 RT는 폐기
        mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(new Cookie("RT", newRt)))
                .andExpect(status().isNoContent());
        // 다른 기기: 401
        mvc.perform(withCsrf(post("/api/auth/refresh")).cookie(new Cookie("RT", other.rt())))
                .andExpect(status().isUnauthorized());
        assertThat(passwordEncoder.matches(NEW_PASSWORD,
                jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id))).isTrue();
    }

    @Test
    void 시나리오6_회원_번호를_보내도_로그인한_본인만_바뀐다() throws Exception {
        long b = createUser("b@x.com", PASSWORD, "비회원");
        createUser("a@x.com", PASSWORD, "에이");
        LoginCookies a = loginAs("a@x.com", PASSWORD, false);

        patchMe(a, "{\"memberId\":" + b + ",\"nickname\":\"바꿈\"}").andExpect(status().isBadRequest());
        mvc.perform(withCsrf(patch("/api/members/" + b)).cookie(a.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"바꿈\"}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(403, 404, 405));
        assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, b))
                .isEqualTo("비회원");
    }
}
