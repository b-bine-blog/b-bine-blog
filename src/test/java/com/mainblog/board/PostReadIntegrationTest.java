package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** 글 상세 (004 T015, US1-5·6·12, FR-005, FR-006, FR-016, D-100 #1·#2). */
class PostReadIntegrationTest extends BoardTestBase {

    @Test
    void 본문은_걸러진_HTML_제목은_글자_그대로() throws Exception {
        BlogFixture b = blog("read-xss", "PUBLIC");
        long id = writePost(b.owner(), b.blogId(), "<b>굵게</b>",
                "<script>alert(1)</script><img src=x onerror=alert(1)> 글");
        JsonNode j = getAs(null, "/api/posts/" + id).json();
        assertThat(j.get("title").asText()).isEqualTo("<b>굵게</b>");
        assertThat(j.get("contentHtml").asText()).doesNotContain("<script", "onerror").contains("글");
        assertThat(j.path("content").asText(null)).isNull();
        assertThat(j.get("canEdit").asBoolean()).isFalse();
        assertThat(j.get("canComment").asBoolean()).isFalse();
        assertThat(j.get("shareable").asBoolean()).isTrue();
    }

    @Test
    void 비공개_일부공개_블로그의_글과_삭제된_글은_404() throws Exception {
        BlogFixture priv = blog("read-priv", "PRIVATE");
        BlogFixture link = blog("read-link", "LINK");
        long p1 = writePost(priv.owner(), priv.blogId(), "비공개", "c");
        long p2 = writePost(link.owner(), link.blogId(), "일부", "c");
        Member outsider = member("밖사람");
        for (long id : new long[] {p1, p2}) {
            Res r = getAs(outsider, "/api/posts/" + id);
            assertThat(r.status()).isEqualTo(404);
            assertThat(r.code()).isEqualTo("POST_NOT_FOUND");
        }
        assertThat(getAs(priv.owner(), "/api/posts/" + p1).json().get("shareable").asBoolean()).isFalse();
        assertThat(deleteAs(link.owner(), "/api/posts/" + p2).status()).isEqualTo(204);
        assertThat(getAs(link.owner(), "/api/posts/" + p2).status()).isEqualTo(404);
        assertThat(getAs(null, "/api/posts/999999").code()).isEqualTo("POST_NOT_FOUND");
    }

    @Test
    void 블로그를_떠난_작성자는_탈퇴한_계정_회원탈퇴는_탈퇴한_회원() throws Exception {
        BlogFixture b = blog("read-author", "PUBLIC");
        Member left = memberOf(b.blogId(), "떠날이");
        Member gone = memberOf(b.blogId(), "탈퇴할");
        long p1 = writePost(left, b.blogId(), "t1", "c");
        long p2 = writePost(gone, b.blogId(), "t2", "c");
        assertThat(deleteAs(left, "/api/blogs/" + b.blogId() + "/members/me").status()).isEqualTo(204);
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?", clock.now(), gone.id());

        JsonNode a1 = getAs(null, "/api/posts/" + p1).json().get("author");
        assertThat(a1.get("nickname").asText()).isEqualTo("탈퇴한 계정");
        assertThat(a1.get("state").asText()).isEqualTo("LEFT_BLOG");
        assertThat(a1.path("id").asText(null)).isNull();
        JsonNode a2 = getAs(null, "/api/posts/" + p2).json().get("author");
        assertThat(a2.get("nickname").asText()).isEqualTo("탈퇴한 회원");
        assertThat(a2.get("state").asText()).isEqualTo("WITHDRAWN");
    }

    @Test
    void 일부공개_블로그_글은_유효한_공유_쿠키로_볼_수_있다() throws Exception {
        BlogFixture link = blog("read-share", "LINK");
        long id = writePost(link.owner(), link.blogId(), "일부", "c");
        String token = getAs(link.owner(), "/api/blogs/" + link.blogId() + "/share-link").json().get("token").asText();
        Member guest = member("손님");
        guest.extraCookies().add(new jakarta.servlet.http.Cookie("BLOG_SHARE_" + link.blogId(), token));
        Res r = getAs(guest, "/api/posts/" + id);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("canComment").asBoolean()).isTrue();
    }
}
