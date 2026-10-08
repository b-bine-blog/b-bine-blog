package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 글 쓰기 (004 T014, US1-1~4·14·15, FR-002~FR-004, D-90, D-92). */
class PostWriteIntegrationTest extends BoardTestBase {

    @Test
    void 멤버가_쓴_글은_201과_상세를_돌려준다() throws Exception {
        BlogFixture b = blog("write-ok", "PUBLIC");
        Member m = memberOf(b.blogId(), "글쓴이");
        Res r = postAs(m, "/api/blogs/" + b.blogId() + "/posts",
                postJson("  첫 글  ", "본문 **굵게**", null, List.of("#여행", "Java")));
        assertThat(r.status()).isEqualTo(201);
        JsonNode j = r.json();
        assertThat(j.get("title").asText()).isEqualTo("첫 글");
        assertThat(j.get("contentHtml").asText()).contains("<strong>굵게</strong>");
        assertThat(j.get("content").asText()).isEqualTo("본문 **굵게**");
        assertThat(j.get("author").get("nickname").asText()).isEqualTo("글쓴이");
        assertThat(j.get("tags")).extracting(JsonNode::asText).containsExactly("여행", "java");
        assertThat(j.get("canEdit").asBoolean()).isTrue();
        assertThat(j.get("blog").get("address").asText()).isEqualTo("write-ok");
    }

    @Test
    void 제목은_1자부터_30자까지() throws Exception {
        BlogFixture b = blog("write-title", "PUBLIC");
        String url = "/api/blogs/" + b.blogId() + "/posts";
        assertThat(postAs(b.owner(), url, postJson("가", "본문", null, null)).status()).isEqualTo(201);
        assertThat(postAs(b.owner(), url, postJson("가".repeat(30), "본문", null, null)).status()).isEqualTo(201);
        Res tooLong = postAs(b.owner(), url, postJson("가".repeat(31), "본문", null, null));
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(tooLong.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(tooLong.body()).contains("title");
        assertThat(postAs(b.owner(), url, postJson("   ", "본문", null, null)).status()).isEqualTo(400);
    }

    @Test
    void 본문은_공백_기호_포함_5000자까지() throws Exception {
        BlogFixture b = blog("write-body", "PUBLIC");
        String url = "/api/blogs/" + b.blogId() + "/posts";
        String exact = "가 #".repeat(1666) + "나 ";
        assertThat(exact.codePointCount(0, exact.length())).isEqualTo(5000);
        assertThat(postAs(b.owner(), url, postJson("t", exact, null, null)).status()).isEqualTo(201);
        Res over = postAs(b.owner(), url, postJson("t", exact + "x", null, null));
        assertThat(over.status()).isEqualTo(400);
        assertThat(over.body()).contains("content");
        assertThat(postAs(b.owner(), url, postJson("t", "  \n ", null, null)).status()).isEqualTo(400);
    }

    @Test
    void 비회원_비멤버_관리자는_쓸_수_없다() throws Exception {
        BlogFixture b = blog("write-deny", "PUBLIC");
        String url = "/api/blogs/" + b.blogId() + "/posts";
        String body = postJson("t", "c", null, null);
        assertThat(postAs(null, url, body).status()).isEqualTo(401);
        Res outsider = postAs(member("남남"), url, body);
        assertThat(outsider.status()).isEqualTo(403);
        assertThat(outsider.code()).isEqualTo("BLOG_FORBIDDEN");
        Res admin = postAs(admin("관리"), url, body);
        assertThat(admin.status()).isEqualTo(403);
        assertThat(admin.code()).isEqualTo("ADMIN_NOT_ALLOWED");
    }

    @Test
    void 폐쇄_예정_블로그에도_쓸_수_있고_정지_회원은_403() throws Exception {
        BlogFixture b = blog("write-closing", "PUBLIC");
        Member m = memberOf(b.blogId(), "정지될");
        jdbc.update("UPDATE blogs SET status = 'CLOSING', close_scheduled_at = ? WHERE id = ?",
                clock.now().plusDays(7), b.blogId());
        assertThat(postAs(b.owner(), "/api/blogs/" + b.blogId() + "/posts", postJson("t", "c", null, null)).status())
                .isEqualTo(201);
        jdbc.update("INSERT INTO blog_sanctions (blog_id, user_id, handled_by, sanction_type, starts_at, ends_at, reason) "
                + "VALUES (?, ?, ?, 'SUSPEND', ?, ?, '테스트')", b.blogId(), m.id(), b.owner().id(), clock.now(),
                clock.now().plusDays(3));
        Res r = postAs(m, "/api/blogs/" + b.blogId() + "/posts", postJson("t", "c", null, null));
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("BLOG_SUSPENDED");
    }

    @Test
    void 다른_블로그의_카테고리나_규칙에_맞지_않는_태그는_400() throws Exception {
        BlogFixture b = blog("write-cat", "PUBLIC");
        BlogFixture other = blog("write-cat2", "PUBLIC");
        long foreign = insertCategory(other.blogId(), "남의 것");
        String url = "/api/blogs/" + b.blogId() + "/posts";
        Res cat = postAs(b.owner(), url, postJson("t", "c", foreign, null));
        assertThat(cat.status()).isEqualTo(400);
        assertThat(cat.body()).contains("categoryId");
        assertThat(postAs(b.owner(), url, postJson("t", "c", null, List.of("c++"))).status()).isEqualTo(400);
        assertThat(postAs(b.owner(), url, postJson("t", "c", null,
                List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"))).status()).isEqualTo(400);
    }
}
