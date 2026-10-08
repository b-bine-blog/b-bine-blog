package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 블로그 숨김·해제 (005 T019, US2-2, FR-012, SC-009, D-103 #7). */
class AdminBlogHideIntegrationTest extends AdminTestBase {

    private static List<String> titles(JsonNode items) {
        List<String> out = new ArrayList<>();
        items.forEach(p -> out.add(p.get("title").asText()));
        return out;
    }

    private static List<String> addresses(JsonNode items) {
        List<String> out = new ArrayList<>();
        items.forEach(b -> out.add(b.get("address").asText()));
        return out;
    }

    private int view(Member who, String address, Cookie cookie) throws Exception {
        var b = get("/api/blogs/address/" + address);
        if (cookie != null) {
            b.cookie(cookie);
        }
        return call(who, b).status();
    }

    @Test
    void 숨기면_목록_검색_피드_통합검색_태그에서_빠지고_멤버만_보고_쓴다() throws Exception {
        Member admin = admin("관리자1");
        BlogFixture b = blog("hide-me", "PUBLIC");
        Member member = memberOf(b.blogId(), "숨김멤버");
        Member subscriber = member("숨김구독");
        subscribe(b.blogId(), subscriber.id());
        Member other = member("숨김남");
        postAs(b.owner(), "/api/blogs/" + b.blogId() + "/posts", postJson("숨김블로그글", "본문", null, List.of("숨김태그")));

        assertThat(addresses(getAs(null, "/api/blogs").json().get("items"))).contains("hide-me");
        assertThat(titles(getAs(null, "/api/feed").json().get("items"))).contains("숨김블로그글");

        Res hide = putAs(admin, "/api/admin/blogs/" + b.blogId() + "/hidden", null);
        assertThat(hide.status()).isEqualTo(204);

        assertThat(addresses(getAs(null, "/api/blogs").json().get("items"))).doesNotContain("hide-me");
        assertThat(getAs(null, "/api/blogs/search?q=hide-me").body()).doesNotContain("hide-me");
        assertThat(titles(getAs(null, "/api/feed").json().get("items"))).doesNotContain("숨김블로그글");
        assertThat(getAs(subscriber, "/api/feed?tab=SUBSCRIBED").body()).doesNotContain("숨김블로그글");
        assertThat(getAs(null, "/api/search?q=" + enc("숨김블로그글")).body()).doesNotContain("숨김블로그글");
        assertThat(getAs(null, "/api/tags/" + enc("숨김태그") + "/posts").body()).doesNotContain("숨김블로그글");

        assertThat(view(null, "hide-me", null)).isEqualTo(404);
        assertThat(view(other, "hide-me", null)).isEqualTo(404);
        assertThat(view(subscriber, "hide-me", null)).isEqualTo(404);
        assertThat(view(admin, "hide-me", null)).isEqualTo(404);
        assertThat(view(b.owner(), "hide-me", null)).isEqualTo(200);
        assertThat(view(member, "hide-me", null)).isEqualTo(200);
        assertThat(postAs(member, "/api/blogs/" + b.blogId() + "/posts", postJson("멤버 글", "본문", null, List.of()))
                .status()).isEqualTo(201);

        assertThat(notificationsOf(b.owner().id())).containsExactly("BLOG_HIDDEN");
        assertThat(notificationsOf(member.id())).doesNotContain("BLOG_HIDDEN");
        assertThat(actions("BLOG_HIDE")).hasSize(1);
        assertThat(((Number) actions("BLOG_HIDE").get(0).get("target_id")).longValue()).isEqualTo(b.blogId());

        Res again = putAs(admin, "/api/admin/blogs/" + b.blogId() + "/hidden", null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("ALREADY_HIDDEN");
    }

    @Test
    void 일부_공개_블로그를_숨기면_공유_링크_쿠키로도_못_들어온다() throws Exception {
        Member admin = admin("관리자2");
        BlogFixture b = blog("hide-link2", "LINK");
        String token = getAs(b.owner(), "/api/blogs/" + b.blogId() + "/share-link").json().get("token").asText();
        Cookie cookie = postAs(null, "/api/blogs/share-access", "{\"token\":\"" + token + "\"}").raw().getCookies()[0];
        assertThat(view(null, "hide-link2", cookie)).isEqualTo(200);
        putAs(admin, "/api/admin/blogs/" + b.blogId() + "/hidden", null);
        assertThat(view(null, "hide-link2", cookie)).isEqualTo(404);
    }

    @Test
    void 해제하면_다시_보이고_기록만_남고_알림은_없다() throws Exception {
        Member admin = admin("관리자3");
        BlogFixture b = blog("unhide-me", "PUBLIC");
        Res notHidden = deleteAs(admin, "/api/admin/blogs/" + b.blogId() + "/hidden");
        assertThat(notHidden.status()).isEqualTo(409);
        assertThat(notHidden.code()).isEqualTo("NOT_HIDDEN");

        putAs(admin, "/api/admin/blogs/" + b.blogId() + "/hidden", null);
        assertThat(deleteAs(admin, "/api/admin/blogs/" + b.blogId() + "/hidden").status()).isEqualTo(204);
        assertThat(view(null, "unhide-me", null)).isEqualTo(200);
        assertThat(addresses(getAs(null, "/api/blogs").json().get("items"))).contains("unhide-me");
        assertThat(actionTypes()).containsExactly("BLOG_HIDE", "BLOG_UNHIDE");
        assertThat(notificationsOf(b.owner().id())).containsExactly("BLOG_HIDDEN");
        assertThat(count("SELECT COUNT(*) FROM blogs WHERE id = ? AND hidden_at IS NULL", b.blogId())).isEqualTo(1);
    }

    @Test
    void 폐쇄된_블로그는_숨기거나_풀_수_없다() throws Exception {
        Member admin = admin("관리자4");
        BlogFixture b = blog("closed-hide", "PUBLIC");
        jdbc.update("UPDATE blogs SET status = 'CLOSED', closed_at = ? WHERE id = ?", clock.now(), b.blogId());
        Res r = putAs(admin, "/api/admin/blogs/" + b.blogId() + "/hidden", null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("BLOG_ALREADY_CLOSED");
        assertThat(deleteAs(admin, "/api/admin/blogs/" + b.blogId() + "/hidden").code())
                .isEqualTo("BLOG_ALREADY_CLOSED");
        assertThat(putAs(admin, "/api/admin/blogs/999999/hidden", null).status()).isEqualTo(404);
        assertThat(actionTypes()).isEmpty();
    }

    @Test
    void 블로그장이나_다른_회원은_숨김과_강제폐쇄를_할_수_없다() throws Exception {
        BlogFixture b = blog("deny-hide", "PUBLIC");
        BlogFixture other = blog("deny-other", "PUBLIC");
        assertThat(putAs(b.owner(), "/api/admin/blogs/" + b.blogId() + "/hidden", null).code()).isEqualTo("ADMIN_ONLY");
        assertThat(postAs(other.owner(), "/api/admin/blogs/" + b.blogId() + "/force-close", null).code())
                .isEqualTo("ADMIN_ONLY");
        assertThat(count("SELECT COUNT(*) FROM blogs WHERE hidden = TRUE OR status <> 'ACTIVE'")).isZero();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
