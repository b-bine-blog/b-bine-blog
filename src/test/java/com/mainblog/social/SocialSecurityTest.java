package com.mainblog.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 003 보안 점검 (T047, SC-010, SEC-10, 4.4, D-22, D-64).
 * - 프로필·팔로워·팔로잉·차단 목록·알림·신고 응답과 로그에 이메일·이름·전화번호가 없다.
 * - 상태를 바꾸는 API는 CSRF 토큰이 없으면 403이다.
 * - 공격 문자열은 JSON 문자열로만 돌아온다. 남의 알림은 읽음·삭제 모두 404다.
 */
@ExtendWith(OutputCaptureExtension.class)
class SocialSecurityTest extends SocialTestBase {

    static final List<String> ATTACKS = List.of(
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "\"><svg onload=alert(1)>",
            "' OR '1'='1");

    @Test
    void 소셜_응답과_로그에_이메일_이름_전화번호가_없다(CapturedOutput output) throws Exception {
        long secretId = createUser("social-secret@test.com", PW, "비밀회원", "홍비밀", "01066667777");
        Member secret = new Member(secretId, "social-secret@test.com",
                loginAs("social-secret@test.com", PW, false), new ArrayList<>());
        Member viewer = member("보는이");
        BlogFixture b = blog("sec-social", "PUBLIC");
        addMember(b.blogId(), secretId, "MEMBER");

        StringBuilder bodies = new StringBuilder();
        bodies.append(postAs(viewer, "/api/users/" + secretId + "/follow", null).body());
        bodies.append(postAs(secret, "/api/users/" + viewer.id() + "/follow", null).body());
        bodies.append(postAs(viewer, "/api/reports", reportJson("USER", secretId, b.blogId(), "SPAM", null)).body());
        bodies.append(postAs(viewer, "/api/reports", reportJson("USER", secretId, b.blogId(), "SPAM", null)).body());
        postAs(viewer, "/api/users/" + secretId + "/block", null);
        for (String url : List.of(
                "/api/users/" + secretId + "/profile",
                "/api/users/" + secretId + "/followers",
                "/api/users/" + secretId + "/following",
                "/api/users/" + viewer.id() + "/followers",
                "/api/me/blocks",
                "/api/notifications",
                "/api/notifications/unread-count",
                "/api/reports/reasons")) {
            Res r = getAs(viewer, url);
            assertThat(r.status()).as(url).isEqualTo(200);
            bodies.append(r.body());
        }
        // 알림을 받은 쪽(새 팔로워 알림)
        bodies.append(getAs(secret, "/api/notifications").body());
        bodies.append(getAs(null, "/api/users/" + secretId + "/profile").body());
        // 오류 응답도 본다
        bodies.append(postAs(secret, "/api/users/" + secretId + "/block", null).body());

        String all = bodies.toString();
        assertThat(all).contains("비밀회원");
        for (String s : List.of("social-secret@test.com", "01066667777", "010-6666-7777", "홍비밀")) {
            assertThat(all).as("응답").doesNotContain(s);
            assertThat(output.getAll()).as("로그").doesNotContain(s);
        }
    }

    @Test
    void CSRF_토큰_없는_쓰기는_403() throws Exception {
        Member me = member("나");
        Member other = member("너");
        BlogFixture b = blog("sec-social-csrf", "PUBLIC");
        long n = insertNotification(me.id(), "NEW_FOLLOWER", "FOLLOW", clock.now());
        List<MockHttpServletRequestBuilder> writes = List.of(
                put("/api/members/me/bio").contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"x\"}"),
                post("/api/users/" + other.id() + "/follow"),
                delete("/api/users/" + other.id() + "/follow"),
                post("/api/users/" + other.id() + "/block"),
                delete("/api/users/" + other.id() + "/block"),
                post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson("USER", other.id(), null, "SPAM", null)),
                post("/api/blogs/" + b.blogId() + "/subscription"),
                post("/api/notifications/" + n + "/read"),
                post("/api/notifications/read-all"),
                delete("/api/notifications/" + n),
                delete("/api/notifications"),
                put("/api/me/notification-settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postLike\":false}"));
        for (MockHttpServletRequestBuilder w : writes) {
            MockHttpServletResponse res = mvc.perform(w.cookie(me.cookies())).andReturn().getResponse();
            assertThat(res.getStatus()).isEqualTo(403);
        }
        assertThat(follows(me.id(), other.id())).isFalse();
        assertThat(blocks(me.id(), other.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports", Integer.class)).isZero();
        assertThat(notificationCount(me.id())).isEqualTo(1);
    }

    @Test
    void 공격_문자열은_JSON_문자열로만_돌아온다() throws Exception {
        Member me = member("나");
        Member reporter = member("신고자");
        for (String attack : ATTACKS) {
            Res bio = putAs(me, "/api/members/me/bio", json(Map.of("bio", attack)));
            assertThat(bio.status()).as(attack).isEqualTo(200);
            jdbc.update("UPDATE users SET nickname = ? WHERE id = ?", attack.substring(0, Math.min(20, attack.length())),
                    me.id());
            Res profile = getAs(null, "/api/users/" + me.id() + "/profile");
            assertThat(profile.raw().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
            assertThat(profile.raw().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(profile.json().get("bio").asText()).isEqualTo(attack);
            assertThat(profile.json().get("nickname").asText()).isEqualTo(attack.substring(0, Math.min(20,
                    attack.length())));

            Res r = postAs(reporter, "/api/reports", reportJson("USER", me.id(), null, "OTHER", attack));
            assertThat(r.raw().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
            assertThat(r.status()).as(attack).isIn(201, 409);
        }
        String detail = jdbc.queryForObject("SELECT reason_detail FROM reports LIMIT 1", String.class);
        assertThat(detail).isEqualTo(ATTACKS.get(0));
    }

    @Test
    void 남의_알림은_읽음과_삭제_모두_404() throws Exception {
        Member me = member("나");
        Member other = member("남");
        long theirs = insertNotification(other.id(), "NEW_FOLLOWER", "FOLLOW", clock.now());
        assertThat(postAs(me, "/api/notifications/" + theirs + "/read", null).status()).isEqualTo(404);
        assertThat(deleteAs(me, "/api/notifications/" + theirs).status()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id = ?", Boolean.class, theirs))
                .isFalse();
        assertThat(notificationCount(other.id())).isEqualTo(1);
    }
}
