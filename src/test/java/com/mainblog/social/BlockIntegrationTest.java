package com.mainblog.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 회원 차단 (003 T041, US8, SC-011, SOC-05, FR-016~FR-020, D-90, D-102 #10~#13). */
class BlockIntegrationTest extends SocialTestBase {

    private Res block(Member who, Member target) throws Exception {
        return postAs(who, "/api/users/" + target.id() + "/block", null);
    }

    private static List<String> titles(JsonNode items) {
        List<String> t = new ArrayList<>();
        items.forEach(i -> t.add(i.get("title").asText()));
        return t;
    }

    private long writeTagged(Member who, long blogId, String title, String tag) throws Exception {
        Res r = postAs(who, "/api/blogs/" + blogId + "/posts", postJson(title, "본문", null, List.of(tag)));
        assertThat(r.status()).isEqualTo(201);
        return r.json().get("id").asLong();
    }

    private Res apply(Member who, long blogId) throws Exception {
        return postAs(who, "/api/blogs/" + blogId + "/join-requests", null);
    }

    private String requestStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM blog_join_requests WHERE id = ?", String.class, id);
    }

    @Test
    void 차단하면_두_방향_팔로우가_끊기고_해제해도_돌아오지_않는다() throws Exception {
        Member a = member("에이");
        Member b = member("비");
        follow(a.id(), b.id());
        follow(b.id(), a.id());

        assertThat(block(a, b).status()).isEqualTo(204);
        assertThat(block(a, b).status()).isEqualTo(204); // 이미 차단해도 204
        assertThat(blocks(a.id(), b.id())).isTrue();
        assertThat(follows(a.id(), b.id())).isFalse();
        assertThat(follows(b.id(), a.id())).isFalse();
        assertThat(postAs(a, "/api/users/" + b.id() + "/follow", null).code()).isEqualTo("FOLLOW_NOT_ALLOWED");
        assertThat(postAs(b, "/api/users/" + a.id() + "/follow", null).code()).isEqualTo("FOLLOW_NOT_ALLOWED");
        assertThat(notificationCount(b.id())).isZero(); // 차단당한 회원에게는 알리지 않는다

        // B가 받는 응답에는 차단 사실이 없다
        Res seenByB = getAs(b, "/api/users/" + a.id() + "/profile");
        assertThat(seenByB.json().get("viewer").get("blocking").asBoolean()).isFalse();
        assertThat(getAs(a, "/api/users/" + b.id() + "/profile").json().get("viewer").get("blocking").asBoolean())
                .isTrue();

        assertThat(deleteAs(a, "/api/users/" + b.id() + "/block").status()).isEqualTo(204);
        assertThat(deleteAs(a, "/api/users/" + b.id() + "/block").status()).isEqualTo(204);
        assertThat(blocks(a.id(), b.id())).isFalse();
        assertThat(follows(a.id(), b.id())).isFalse();
        assertThat(follows(b.id(), a.id())).isFalse();
    }

    @Test
    void 차단한_쪽에서만_글과_댓글이_빠지고_같은_블로그_멤버면_보인다() throws Exception {
        BlogFixture k = blog("blk-k", "PUBLIC");
        BlogFixture j = blog("blk-j", "PUBLIC");
        BlogFixture l = blog("blk-l", "PUBLIC");
        Member a = l.owner();
        Member b = memberOf(k.blogId(), "비");
        addMember(j.blogId(), b.id(), "MEMBER");
        addMember(j.blogId(), a.id(), "MEMBER");
        writeTagged(b, k.blogId(), "케이차단글", "차단태그");
        writeTagged(b, j.blogId(), "제이차단글", "차단태그");
        writeTagged(a, l.blogId(), "에이차단글", "차단태그");
        long ownerPost = writePost(k.owner(), k.blogId(), "케이주인글", "본문");
        postAs(b, "/api/posts/" + ownerPost + "/comments", json(Map.of("content", "비의댓글")));

        assertThat(block(a, b).status()).isEqualTo(204);

        assertThat(titles(getAs(a, "/api/blogs/" + k.blogId() + "/posts").json().get("items")))
                .containsExactly("케이주인글");
        assertThat(titles(getAs(a, "/api/blogs/" + j.blogId() + "/posts").json().get("items")))
                .containsExactly("제이차단글");
        assertThat(titles(getAs(a, "/api/tags/" + URLEncoder.encode("차단태그", StandardCharsets.UTF_8) + "/posts")
                .json().get("items"))).containsExactlyInAnyOrder("제이차단글", "에이차단글");
        assertThat(titles(getAs(a, "/api/feed?tab=LATEST").json().get("items")))
                .doesNotContain("케이차단글").contains("제이차단글");
        assertThat(titles(getAs(a, "/api/search?q=" + URLEncoder.encode("차단글", StandardCharsets.UTF_8)
                + "&type=TITLE").json().get("posts").get("items")))
                .containsExactlyInAnyOrder("제이차단글", "에이차단글");
        assertThat(getAs(a, "/api/posts/" + ownerPost + "/comments").body()).doesNotContain("비의댓글");

        // B에게는 A의 글이 그대로 보이고, 비회원·다른 회원에게는 B 글이 그대로 보인다
        assertThat(titles(getAs(b, "/api/blogs/" + l.blogId() + "/posts").json().get("items")))
                .containsExactly("에이차단글");
        assertThat(titles(getAs(null, "/api/blogs/" + k.blogId() + "/posts").json().get("items")))
                .contains("케이차단글");
        assertThat(getAs(k.owner(), "/api/posts/" + ownerPost + "/comments").body()).contains("비의댓글");
    }

    @Test
    void 차단한_회원의_행동은_알리지_않고_끌_수_없는_알림은_그대로_간다() throws Exception {
        BlogFixture mine = blog("blk-noti", "PUBLIC");
        Member a = mine.owner();
        Member b = member("비");
        long post = writePost(a, mine.blogId(), "에이글", "본문");
        long myComment = postAs(a, "/api/posts/" + post + "/comments", json(Map.of("content", "에이댓글"))).json()
                .get("id").asLong();
        assertThat(block(a, b).status()).isEqualTo(204);

        postAs(b, "/api/posts/" + post + "/comments", json(Map.of("content", "비댓글")));
        postAs(b, "/api/posts/" + post + "/comments", json(Map.of("content", "비답글", "parentId", myComment)));
        putAs(b, "/api/posts/" + post + "/like", null);
        assertThat(postAs(b, "/api/blogs/" + mine.blogId() + "/subscription", null).status()).isEqualTo(204);
        postAs(b, "/api/users/" + a.id() + "/follow", null);
        assertThat(notificationCount(a.id())).isZero();

        // B가 블로그장인 블로그에 A가 낸 신청을 B가 거절하면 결과 알림은 간다(끌 수 없는 운영 알림)
        Member bOwner = b;
        long bBlog = createBlog(bOwner, "blk-bblog", "PUBLIC", "APPROVAL");
        long req = apply(a, bBlog).json().get("id").asLong();
        postAs(bOwner, "/api/blogs/" + bBlog + "/join-requests/" + req + "/rejection", null);
        assertThat(notificationsOf(a.id())).containsExactly("JOIN_REJECTED");
    }

    @Test
    void 블로그장이_차단하면_대기_중_신청이_자동_거절되고_부블로그장의_차단은_영향_없다() throws Exception {
        Member o = member("블로그장");
        long blogId = createBlog(o, "blk-join", "PUBLIC", "APPROVAL");
        Member manager = memberOf(blogId, "부장");
        setManagerPerms(blogId, manager.id(), false, true, false);
        Member b = member("비");
        Member c = member("씨");
        long reqB = apply(b, blogId).json().get("id").asLong();
        long reqC = apply(c, blogId).json().get("id").asLong();

        assertThat(block(o, b).status()).isEqualTo(204);
        assertThat(requestStatus(reqB)).isEqualTo("AUTO_REJECTED");
        assertThat(jdbc.queryForObject("SELECT processed_at FROM blog_join_requests WHERE id = ?", Object.class, reqB))
                .isNotNull();
        assertThat(notificationsOf(b.id())).containsExactly("JOIN_REJECTED");
        Res again = apply(b, blogId);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("JOIN_REAPPLY_TOO_SOON");
        // 대기 목록에서 빠진다
        assertThat(getAs(o, "/api/blogs/" + blogId + "/join-requests").body()).doesNotContain("\"비\"");

        assertThat(block(manager, c).status()).isEqualTo(204);
        assertThat(requestStatus(reqC)).isEqualTo("PENDING");

        // 차단된 뒤 새로 낸 신청도 자동 거절(기존 동작)
        Member d = member("디");
        assertThat(block(o, d).status()).isEqualTo(204);
        Res late = apply(d, blogId);
        assertThat(late.json().get("status").asText()).isEqualTo("REJECTED");
    }

    @Test
    void 차단_목록은_최근_순_20개씩이고_자기_자신과_관리자는_막는다() throws Exception {
        Member a = member("에이");
        for (int i = 0; i < 25; i++) {
            long id = createUser("blk" + i + "@test.com", PW, "차단" + i);
            jdbc.update("INSERT INTO user_blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)", a.id(), id,
                    clock.now().minusMinutes(25 - i));
        }
        JsonNode page1 = getAs(a, "/api/me/blocks").json();
        assertThat(page1.get("items").size()).isEqualTo(20);
        assertThat(page1.get("totalElements").asLong()).isEqualTo(25);
        assertThat(page1.get("totalPages").asInt()).isEqualTo(2);
        JsonNode first = page1.get("items").get(0);
        assertThat(first.get("nickname").asText()).isEqualTo("차단24");
        assertThat(first.get("blockedAt").asText()).isNotBlank();
        assertThat(page1.toString()).doesNotContain("@test.com");
        assertThat(getAs(a, "/api/me/blocks?page=2").json().get("items").size()).isEqualTo(5);
        assertThat(getAs(null, "/api/me/blocks").status()).isEqualTo(401);

        Res self = block(a, a);
        assertThat(self.status()).isEqualTo(400);
        assertThat(self.code()).isEqualTo("SELF_NOT_ALLOWED");
        Member adm = admin("관리");
        Res byAdmin = block(adm, a);
        assertThat(byAdmin.status()).isEqualTo(403);
        assertThat(byAdmin.code()).isEqualTo("ADMIN_NOT_ALLOWED");
        assertThat(block(a, adm).status()).isEqualTo(404);
        assertThat(postAs(a, "/api/users/999999/block", null).status()).isEqualTo(404);
    }
}
