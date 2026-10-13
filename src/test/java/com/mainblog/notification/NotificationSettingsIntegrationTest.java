package com.mainblog.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.mainblog.social.SocialTestBase;
import org.junit.jupiter.api.Test;

/** 알림 설정 (003 T028, US5-1~4, SC-005, FR-054, FR-055, FR-058, D-102 #26). */
class NotificationSettingsIntegrationTest extends SocialTestBase {

    private static final String[] KEYS = {"postComment", "commentReply", "postLike", "newFollower", "blogSubscribed",
            "joinRequest", "joinResult", "reportResult", "notice"};

    @Test
    void 처음에는_9가지_모두_켜짐_30일이고_보낸_항목만_바뀐다() throws Exception {
        Member m = member("설정");
        JsonNode first = getAs(m, "/api/me/notification-settings").json();
        for (String k : KEYS) {
            assertThat(first.get(k).asBoolean()).as(k).isTrue();
        }
        assertThat(first.get("retentionDays").asInt()).isEqualTo(30);
        assertThat(first.size()).isEqualTo(10);

        JsonNode changed = putAs(m, "/api/me/notification-settings", "{\"postLike\":false}").json();
        assertThat(changed.get("postLike").asBoolean()).isFalse();
        assertThat(changed.get("postComment").asBoolean()).isTrue();
        JsonNode again = putAs(m, "/api/me/notification-settings", "{\"notice\":false,\"retentionDays\":7}").json();
        assertThat(again.get("postLike").asBoolean()).isFalse();
        assertThat(again.get("notice").asBoolean()).isFalse();
        assertThat(again.get("retentionDays").asInt()).isEqualTo(7);
        assertThat(getAs(m, "/api/me/notification-settings").json()).isEqualTo(again);
    }

    @Test
    void 끌_수_없는_알림_키와_7_30_밖의_보관_기간은_400() throws Exception {
        Member m = member("거부");
        Res closing = putAs(m, "/api/me/notification-settings", "{\"blogClosing\":false}");
        assertThat(closing.status()).isEqualTo(400);
        assertThat(closing.json().get("fieldErrors").get(0).get("field").asText()).isEqualTo("blogClosing");
        assertThat(closing.json().get("fieldErrors").get(0).get("code").asText()).isEqualTo("NotAllowed");
        assertThat(putAs(m, "/api/me/notification-settings", "{\"retentionDays\":14}").status()).isEqualTo(400);
        assertThat(putAs(m, "/api/me/notification-settings", "{\"postLike\":\"no\"}").status()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_settings", Integer.class)).isZero();
        assertThat(getAs(null, "/api/me/notification-settings").status()).isEqualTo(401);
    }

    @Test
    void 좋아요_알림을_끄면_받은_것은_그대로_새_알림은_없고_다시_켜도_꺼_둔_동안_몫은_생기지_않는다() throws Exception {
        BlogFixture b = blog("set-like", "PUBLIC");
        long p1 = writePost(b.owner(), b.blogId(), "글1", "c");
        long p2 = writePost(b.owner(), b.blogId(), "글2", "c");
        long p3 = writePost(b.owner(), b.blogId(), "글3", "c");
        Member fan = member("팬");
        for (long p : new long[] {p1, p2, p3}) {
            putAs(fan, "/api/posts/" + p + "/like", null);
        }
        assertThat(notificationsOf(b.owner().id())).hasSize(3);

        putAs(b.owner(), "/api/me/notification-settings", "{\"postLike\":false}");
        putAs(member("팬2"), "/api/posts/" + p1 + "/like", null);
        assertThat(notificationsOf(b.owner().id())).hasSize(3);

        putAs(b.owner(), "/api/me/notification-settings", "{\"postLike\":true}");
        assertThat(notificationsOf(b.owner().id())).hasSize(3);
        putAs(member("팬3"), "/api/posts/" + p1 + "/like", null);
        assertThat(notificationsOf(b.owner().id())).hasSize(4);
    }

    @Test
    void 모두_꺼도_폐쇄_예정_알림은_온다() throws Exception {
        BlogFixture b = blog("set-closing", "PUBLIC");
        Member m = memberOf(b.blogId(), "다끔");
        StringBuilder body = new StringBuilder("{");
        for (String k : KEYS) {
            body.append('"').append(k).append("\":false,");
        }
        body.setLength(body.length() - 1);
        body.append('}');
        assertThat(putAs(m, "/api/me/notification-settings", body.toString()).status()).isEqualTo(200);
        postAs(b.owner(), "/api/blogs/" + b.blogId() + "/closing", null);
        assertThat(notificationsOf(m.id())).containsExactly("BLOG_CLOSING");
    }

    @Test
    void 설정은_본인_것만_바뀐다() throws Exception {
        Member a = member("에이");
        Member other = member("비");
        putAs(a, "/api/me/notification-settings", "{\"notice\":false}");
        assertThat(getAs(other, "/api/me/notification-settings").json().get("notice").asBoolean()).isTrue();
    }
}
