package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 관리자 블로그 검색·상세 (005 T018, US2-1, FR-011, D-103 #6). */
class AdminBlogQueryIntegrationTest extends AdminTestBase {

    private static List<String> addresses(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("items").forEach(b -> out.add(b.get("address").asText()));
        return out;
    }

    @Test
    void 이름_주소_블로그장_닉네임으로_찾고_공개_범위와_관계없이_나온다() throws Exception {
        Member admin = admin("관리자1");
        Member owner = member("여행장");
        createBlog(owner, "travel-pub", "PUBLIC", "FREE");
        createBlog(owner, "travel-link", "LINK", "FREE");
        createBlog(owner, "travel-priv", "PRIVATE", "FREE");
        long named = createBlog(member("요리장"), "cook-blog", "PUBLIC", "FREE");
        jdbc.update("UPDATE blogs SET name = '맛있는 여행 요리' WHERE id = ?", named);
        createBlog(member("무관"), "other-blog", "PUBLIC", "FREE");

        assertThat(addresses(getAs(admin, "/api/admin/blogs?q=travel").json()))
                .containsExactlyInAnyOrder("travel-pub", "travel-link", "travel-priv");
        assertThat(addresses(getAs(admin, "/api/admin/blogs?q=" + enc("여행")).json()))
                .containsExactlyInAnyOrder("travel-pub", "travel-link", "travel-priv", "cook-blog");
        JsonNode all = getAs(admin, "/api/admin/blogs").json();
        assertThat(all.get("totalElements").asInt()).isEqualTo(5);
        assertThat(all.get("items").get(0).get("owner").get("nickname").asText()).isNotBlank();
    }

    @Test
    void 상태를_보여주고_거를_수_있으며_폐쇄된_블로그는_30일_정리_전까지만_나온다() throws Exception {
        Member admin = admin("관리자2");
        Member o = member("상태장");
        createOwnedBlog(o.id(), "st-active", "ACTIVE");
        createOwnedBlog(o.id(), "st-closing", "CLOSING");
        long closed = createOwnedBlog(o.id(), "st-closed", "ACTIVE");
        jdbc.update("UPDATE blogs SET status = 'CLOSED', closed_at = ? WHERE id = ?", clock.now().minusDays(29), closed);
        long old = createOwnedBlog(o.id(), "st-old", "ACTIVE");
        jdbc.update("UPDATE blogs SET status = 'CLOSED', closed_at = ? WHERE id = ?", clock.now().minusDays(31), old);
        long hidden = createOwnedBlog(o.id(), "st-hidden", "ACTIVE");
        jdbc.update("UPDATE blogs SET hidden = TRUE, hidden_at = ? WHERE id = ?", clock.now(), hidden);

        assertThat(addresses(getAs(admin, "/api/admin/blogs?q=st-").json()))
                .containsExactlyInAnyOrder("st-active", "st-closing", "st-closed", "st-hidden");
        assertThat(addresses(getAs(admin, "/api/admin/blogs?status=CLOSING").json())).containsExactly("st-closing");
        assertThat(addresses(getAs(admin, "/api/admin/blogs?status=CLOSED").json())).containsExactly("st-closed");
        JsonNode h = getAs(admin, "/api/admin/blogs?status=HIDDEN").json();
        assertThat(addresses(h)).containsExactly("st-hidden");
        assertThat(h.get("items").get(0).get("hidden").asBoolean()).isTrue();
        assertThat(h.get("items").get(0).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(getAs(admin, "/api/admin/blogs?status=GONE").status()).isEqualTo(400);
    }

    @Test
    void 멤버_수와_글_수는_삭제_글을_빼고_숨긴_글은_센다() throws Exception {
        Member admin = admin("관리자3");
        BlogFixture b = blog("count-blog", "PUBLIC");
        for (int i = 0; i < 3; i++) {
            addMember(b.blogId(), member("멤버" + i).id(), "MEMBER");
        }
        for (int i = 0; i < 13; i++) {
            insertPost(b.blogId(), b.owner().id(), "글" + i, "본문", clock.now());
        }
        long deleted = insertPost(b.blogId(), b.owner().id(), "지운 글", "본문", clock.now());
        jdbc.update("UPDATE posts SET status = 'DELETED', deleted_at = ? WHERE id = ?", clock.now(), deleted);
        jdbc.update("UPDATE posts SET status = 'HIDDEN', hidden_at = ? WHERE title = '글0'", clock.now());
        jdbc.update("DELETE FROM posts WHERE title = '글12'");

        JsonNode item = getAs(admin, "/api/admin/blogs?q=count-blog").json().get("items").get(0);
        assertThat(item.get("memberCount").asInt()).isEqualTo(4);
        assertThat(item.get("postCount").asInt()).isEqualTo(12);
        assertThat(item.get("owner").get("id").asLong()).isEqualTo(b.owner().id());
    }

    @Test
    void 상세에_부블로그장_목록_대기중_위임요청_지금_블로그장_경고_수가_있다() throws Exception {
        Member admin = admin("관리자4");
        BlogFixture b = blog("detail-blog", "PRIVATE");
        Member s1 = member("부장1");
        addMember(b.blogId(), s1.id(), "MANAGER");
        clock.advance(java.time.Duration.ofDays(1));
        Member s2 = member("부장2");
        addMember(b.blogId(), s2.id(), "MANAGER");
        jdbc.update("INSERT INTO blog_delegations (to_user_id, blog_id, from_user_id, status, requested_at, expires_at) "
                + "VALUES (?, ?, ?, 'PENDING', ?, ?)", s1.id(), b.blogId(), b.owner().id(), clock.now(),
                clock.now().plusDays(7));
        insertWarning(b.blogId(), b.owner().id(), admin.id(), clock.now().minusDays(10));
        insertWarning(b.blogId(), b.owner().id(), admin.id(), clock.now().minusDays(400));

        admin = relogin(admin);
        Res r = getAs(admin, "/api/admin/blogs/" + b.blogId());
        assertThat(r.status()).isEqualTo(200);
        JsonNode d = r.json();
        assertThat(d.get("visibility").asText()).isEqualTo("PRIVATE");
        assertThat(d.get("managers").findValuesAsText("nickname")).containsExactly("부장1", "부장2");
        assertThat(d.get("pendingDelegation").asBoolean()).isTrue();
        assertThat(d.get("ownerActiveWarningCount").asInt()).isEqualTo(1);
        assertThat(r.body()).doesNotContain(b.owner().email());
        assertThat(getAs(admin, "/api/admin/blogs/999999").status()).isEqualTo(404);
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
