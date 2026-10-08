package com.mainblog.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.mainblog.social.SocialTestBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 알림함 목록·탭·읽음·삭제·이동할 곳 (003 T008, US1-1~5·8, FR-029~FR-031, FR-033, D-102 #17~#19·#21·#28). */
class NotificationInboxIntegrationTest extends SocialTestBase {

    private List<String> types(JsonNode slice) {
        List<String> out = new ArrayList<>();
        slice.get("items").forEach(n -> out.add(n.get("type").asText()));
        return out;
    }

    @Test
    void 탭마다_그_탭_알림만_보이고_팔로우_탭은_새_팔로워와_구독을_함께_본다() throws Exception {
        Member m = member("받는이");
        insertNotification(m.id(), "POST_COMMENT", "COMMENT", clock.now().minusMinutes(6));
        insertNotification(m.id(), "POST_LIKE", "LIKE", clock.now().minusMinutes(5));
        insertNotification(m.id(), "NEW_FOLLOWER", "FOLLOW", clock.now().minusMinutes(4));
        insertNotification(m.id(), "BLOG_SUBSCRIBED", "SUBSCRIBE", clock.now().minusMinutes(3));
        insertNotification(m.id(), "BLOG_CLOSING", "BLOG", clock.now().minusMinutes(2));
        insertNotification(m.id(), "MEMBER_WARNED", "OPERATION", clock.now().minusMinutes(1));

        assertThat(types(getAs(m, "/api/notifications").json())).containsExactly("MEMBER_WARNED", "BLOG_CLOSING",
                "BLOG_SUBSCRIBED", "NEW_FOLLOWER", "POST_LIKE", "POST_COMMENT");
        assertThat(types(getAs(m, "/api/notifications?tab=COMMENT").json())).containsExactly("POST_COMMENT");
        assertThat(types(getAs(m, "/api/notifications?tab=LIKE").json())).containsExactly("POST_LIKE");
        JsonNode follow = getAs(m, "/api/notifications?tab=FOLLOW").json();
        assertThat(types(follow)).containsExactly("BLOG_SUBSCRIBED", "NEW_FOLLOWER");
        follow.get("items").forEach(n -> assertThat(n.get("tab").asText()).isEqualTo("FOLLOW"));
        assertThat(types(getAs(m, "/api/notifications?tab=BLOG").json())).containsExactly("BLOG_CLOSING");
        assertThat(types(getAs(m, "/api/notifications?tab=OPERATION").json())).containsExactly("MEMBER_WARNED");
        assertThat(getAs(m, "/api/notifications?tab=NOPE").status()).isEqualTo(400);
    }

    @Test
    void 최신순_20개씩이고_before로_다음_묶음을_받는다() throws Exception {
        Member m = member("많이");
        insertNotifications(m.id(), "POST_LIKE", "LIKE", 25);
        JsonNode first = getAs(m, "/api/notifications").json();
        assertThat(first.get("items")).hasSize(20);
        assertThat(first.get("hasMore").asBoolean()).isTrue();
        long firstId = first.get("items").get(0).get("id").asLong();
        long lastId = first.get("items").get(19).get("id").asLong();
        assertThat(firstId).isGreaterThan(lastId);
        assertThat(first.get("nextBefore").asLong()).isEqualTo(lastId);

        JsonNode second = getAs(m, "/api/notifications?before=" + lastId).json();
        assertThat(second.get("items")).hasSize(5);
        assertThat(second.get("hasMore").asBoolean()).isFalse();
        assertThat(second.get("nextBefore").isNull()).isTrue();
        assertThat(second.get("items").get(0).get("id").asLong()).isLessThan(lastId);
    }

    @Test
    void 목록을_받는_것만으로는_읽음이_되지_않고_하나_누르면_그것만_읽음() throws Exception {
        Member m = member("읽기");
        long a = insertNotification(m.id(), "POST_LIKE", "LIKE", clock.now().minusMinutes(2));
        long b = insertNotification(m.id(), "POST_LIKE", "LIKE", clock.now().minusMinutes(1));
        getAs(m, "/api/notifications");
        assertThat(getAs(m, "/api/notifications/unread-count").json().get("unreadCount").asInt()).isEqualTo(2);
        assertThat(postAs(m, "/api/notifications/" + a + "/read", null).status()).isEqualTo(204);
        assertThat(postAs(m, "/api/notifications/" + a + "/read", null).status()).isEqualTo(204);
        JsonNode items = getAs(m, "/api/notifications").json().get("items");
        assertThat(items.get(0).get("id").asLong()).isEqualTo(b);
        assertThat(items.get(0).get("read").asBoolean()).isFalse();
        assertThat(items.get(1).get("read").asBoolean()).isTrue();
    }

    @Test
    void 모두_읽음은_보고_있는_탭만() throws Exception {
        Member m = member("모두");
        insertNotification(m.id(), "POST_COMMENT", "COMMENT", clock.now().minusMinutes(3));
        insertNotification(m.id(), "COMMENT_REPLY", "COMMENT", clock.now().minusMinutes(2));
        insertNotification(m.id(), "POST_LIKE", "LIKE", clock.now().minusMinutes(1));
        Res r = postAs(m, "/api/notifications/read-all?tab=COMMENT", null);
        assertThat(r.json().get("updated").asInt()).isEqualTo(2);
        assertThat(getAs(m, "/api/notifications/unread-count").json().get("unreadCount").asInt()).isEqualTo(1);
        assertThat(postAs(m, "/api/notifications/read-all", null).json().get("updated").asInt()).isEqualTo(1);
        assertThat(getAs(m, "/api/notifications/unread-count").json().get("unreadCount").asInt()).isZero();
    }

    @Test
    void 하나_삭제_탭_전체_삭제는_그_탭만_ALL은_모두_행을_바로_지운다() throws Exception {
        Member m = member("삭제");
        long one = insertNotification(m.id(), "POST_LIKE", "LIKE", clock.now().minusMinutes(5));
        insertNotification(m.id(), "POST_LIKE", "LIKE", clock.now().minusMinutes(4));
        insertNotification(m.id(), "POST_COMMENT", "COMMENT", clock.now().minusMinutes(3));
        insertNotification(m.id(), "COMMENT_REPLY", "COMMENT", clock.now().minusMinutes(2));
        insertNotification(m.id(), "BLOG_CLOSING", "BLOG", clock.now().minusMinutes(1));

        assertThat(deleteAs(m, "/api/notifications/" + one).status()).isEqualTo(204);
        assertThat(notificationCount(m.id())).isEqualTo(4);
        Res tab = deleteAs(m, "/api/notifications?tab=COMMENT");
        assertThat(tab.json().get("deleted").asInt()).isEqualTo(2);
        assertThat(notificationsOf(m.id())).containsExactly("POST_LIKE", "BLOG_CLOSING");
        assertThat(deleteAs(m, "/api/notifications?tab=ALL").json().get("deleted").asInt()).isEqualTo(2);
        assertThat(notificationCount(m.id())).isZero();
    }

    @Test
    void 남의_알림은_보이지_않고_읽음_삭제는_404_비회원은_401() throws Exception {
        Member me = member("나");
        Member other = member("남");
        long theirs = insertNotification(other.id(), "POST_LIKE", "LIKE", clock.now());
        assertThat(getAs(me, "/api/notifications").json().get("items")).isEmpty();
        assertThat(postAs(me, "/api/notifications/" + theirs + "/read", null).code()).isEqualTo("NOT_FOUND");
        assertThat(deleteAs(me, "/api/notifications/" + theirs).code()).isEqualTo("NOT_FOUND");
        assertThat(deleteAs(me, "/api/notifications?tab=ALL").json().get("deleted").asInt()).isZero();
        assertThat(notificationCount(other.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id = ?", Boolean.class, theirs))
                .isFalse();
        assertThat(getAs(null, "/api/notifications").status()).isEqualTo(401);
        assertThat(getAs(null, "/api/notifications/unread-count").status()).isEqualTo(401);
    }

    @Test
    void 종류별_이동할_곳과_대상이_없어지면_null() throws Exception {
        BlogFixture b = blog("link-blog", "PUBLIC");
        Member m = memberOf(b.blogId(), "링크");
        Member fan = member("팬");
        long post = writePost(m, b.blogId(), "글", "내용");
        long comment = postAs(fan, "/api/posts/" + post + "/comments", "{\"content\":\"좋아요\"}").json()
                .get("id").asLong();
        Member adm = admin("관리");
        long notice = postAs(adm, "/api/notices", postJson("공지", "내용", null, null)).json().get("id").asLong();
        jdbc.update("DELETE FROM notifications");

        long cmt = insertNotification(m.id(), "POST_COMMENT", "COMMENT", "COMMENT", comment, fan.id(), clock.now());
        long like = insertNotification(m.id(), "POST_LIKE", "LIKE", "POST", post, fan.id(), clock.now());
        long ntc = insertNotification(m.id(), "NOTICE", "OPERATION", "POST", notice, adm.id(), clock.now());
        long fol = insertNotification(m.id(), "NEW_FOLLOWER", "FOLLOW", "USER", fan.id(), fan.id(), clock.now());
        long sub = insertNotification(m.id(), "BLOG_SUBSCRIBED", "SUBSCRIBE", "BLOG", b.blogId(), fan.id(), clock.now());
        long closing = insertNotification(m.id(), "BLOG_CLOSING", "BLOG", "BLOG", b.blogId(), null, clock.now());
        long join = insertNotification(m.id(), "JOIN_REQUESTED", "BLOG", "BLOG", b.blogId(), fan.id(), clock.now());
        long deleg = insertNotification(m.id(), "DELEGATION_REQUESTED", "BLOG", "BLOG", b.blogId(), null, clock.now());
        long warn = insertNotification(m.id(), "MEMBER_WARNED", "OPERATION", "BLOG", b.blogId(), null, clock.now());
        long result = insertNotification(m.id(), "JOIN_APPROVED", "BLOG", "BLOG", b.blogId(), null, clock.now());

        java.util.Map<Long, String> links = linksOf(m);
        assertThat(links.get(cmt)).isEqualTo("/blog/link-blog/posts/" + post);
        assertThat(links.get(like)).isEqualTo("/blog/link-blog/posts/" + post);
        assertThat(links.get(ntc)).isEqualTo("/notices/" + notice);
        assertThat(links.get(fol)).isEqualTo("/profile/" + fan.id());
        assertThat(links.get(sub)).isEqualTo("/blog/link-blog");
        assertThat(links.get(closing)).isEqualTo("/blog/link-blog");
        assertThat(links.get(join)).isEqualTo("/blog/link-blog/manage?tab=requests");
        assertThat(links.get(deleg)).isEqualTo("/blog/link-blog");
        assertThat(links.get(warn)).isNull();
        assertThat(links.get(result)).isNull();

        // 글이 지워지면 글·댓글·좋아요 알림의 link는 null, 회원이 탈퇴하면 새 팔로워 link도 null
        jdbc.update("UPDATE posts SET status = 'DELETED' WHERE id = ?", post);
        withdraw(fan.id());
        links = linksOf(m);
        assertThat(links.get(cmt)).isNull();
        assertThat(links.get(like)).isNull();
        assertThat(links.get(fol)).isNull();
        assertThat(links.get(sub)).isEqualTo("/blog/link-blog");
    }

    private java.util.Map<Long, String> linksOf(Member m) throws Exception {
        java.util.Map<Long, String> out = new java.util.HashMap<>();
        getAs(m, "/api/notifications").json().get("items").forEach(n -> out.put(n.get("id").asLong(),
                n.get("link").isNull() ? null : n.get("link").asText()));
        return out;
    }

    @Test
    void 응답에는_행동한_회원의_이메일이_없고_내용은_닉네임으로만() throws Exception {
        Member owner = member("블장");
        long blogId = createBlog(owner, "nick-blog", "PUBLIC", "APPROVAL");
        Member fan = member("닉네임팬");
        postAs(fan, "/api/blogs/" + blogId + "/join-requests", null);
        String body = getAs(owner, "/api/notifications").body();
        assertThat(body).contains("닉네임팬").doesNotContain(fan.email()).doesNotContain("01012345678")
                .doesNotContain("actor");
    }
}
