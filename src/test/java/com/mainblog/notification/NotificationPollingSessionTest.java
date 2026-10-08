package com.mainblog.notification;

import static com.mainblog.support.AuthTestSupport.cookieValue;
import static com.mainblog.support.AuthTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.mainblog.social.SocialTestBase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 30초 알림 확인과 로그인 연장 제외 (003 T009, US1-6·7, SC-001, SC-012, D-62, D-72, D-102 #20, research R-05).
 * 화면(api.js)처럼 AT가 만료돼 401이면 refresh를 한 번 부르고 이어서 확인한다.
 */
class NotificationPollingSessionTest extends SocialTestBase {

    @Test
    void 안_읽은_수와_확인_간격을_주고_150건이면_100에서_멈춘다() throws Exception {
        Member m = member("폴링");
        Res empty = getAs(m, "/api/notifications/unread-count");
        assertThat(empty.json().get("unreadCount").asInt()).isZero();
        assertThat(empty.json().get("pollIntervalSeconds").asInt()).isEqualTo(30);
        insertNotifications(m.id(), "POST_LIKE", "LIKE", 150);
        assertThat(getAs(m, "/api/notifications/unread-count").json().get("unreadCount").asInt()).isEqualTo(100);
    }

    @Test
    void 새_알림은_다음_확인에_잡힌다() throws Exception {
        BlogFixture b = blog("poll-new", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        assertThat(getAs(b.owner(), "/api/notifications/unread-count").json().get("unreadCount").asInt()).isZero();
        postAs(member("댓글러"), "/api/posts/" + post + "/comments", "{\"content\":\"안녕\"}");
        clock.advance(Duration.ofSeconds(30));
        assertThat(getAs(b.owner(), "/api/notifications/unread-count").json().get("unreadCount").asInt()).isEqualTo(1);
    }

    /** 미체크 로그인 + 헤더 없는 unread-count만 30초마다 → 30분 뒤 로그인이 끝난다. activityAt 분에 한 번 활동 헤더를 붙인다. */
    private boolean pollUntil(int minutes, Integer activityAt) throws Exception {
        long userId = createUser("poll@test.com", PW, "폴러");
        LoginCookies login = loginAs("poll@test.com", PW, false);
        String at = login.at();
        String rt = login.rt();
        for (int sec = 30; sec <= minutes * 60; sec += 30) {
            clock.setTo(BASE_TIME.plusSeconds(sec));
            var req = get("/api/notifications/unread-count").cookie(new Cookie("AT", at), new Cookie("RT", rt));
            if (activityAt != null && sec == activityAt * 60) {
                req.header("X-User-Activity", "1");
            }
            int status = mvc.perform(req).andReturn().getResponse().getStatus();
            if (status == 401) {
                MockHttpServletResponse refreshed = mvc.perform(withCsrf(post("/api/auth/refresh"))
                        .cookie(new Cookie("RT", rt))).andReturn().getResponse();
                if (refreshed.getStatus() != 204) {
                    return false; // 로그인이 끝나 화면은 폴링을 멈춘다
                }
                at = cookieValue(refreshed, "AT");
                rt = cookieValue(refreshed, "RT");
            } else {
                assertThat(status).isEqualTo(200);
            }
        }
        assertThat(userId).isPositive();
        return true;
    }

    @Test
    void SC012_헤더_없는_확인만_31분_보내면_로그인이_끝난다() throws Exception {
        assertThat(pollUntil(31, null)).isFalse();
    }

    @Test
    void 활동_헤더가_붙은_요청이_있으면_그때부터_30분_유지된다() throws Exception {
        assertThat(pollUntil(45, 20)).isTrue();
    }
}
