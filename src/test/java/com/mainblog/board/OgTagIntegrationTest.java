package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 글 링크 미리보기 (004 T048, US9, FR-047~FR-050, SC-010, D-100 #5·#6·#14·#15). */
class OgTagIntegrationTest extends BoardTestBase {

    private String page(String path) throws Exception {
        Res r = getAs(null, path);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.raw().getContentType()).startsWith("text/html");
        return r.body();
    }

    @Test
    void 공개_글은_주소_제목_닉네임_요약_첫_이미지를_채운다() throws Exception {
        BlogFixture b = blog("og-pub", "PUBLIC");
        String nick = jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, b.owner().id());
        long id = writePost(b.owner(), b.blogId(), "제주 여행",
                "# 첫날\n\n**바다**를 봤다 " + "가".repeat(120) + "\n\n![a](/files/0b0e9c7e-1111-4222-8333-444455556666.png)");
        String html = page("/blog/og-pub/posts/" + id);
        assertThat(html).contains("<meta property=\"og:url\" content=\"http://localhost:8080/blog/og-pub/posts/" + id + "\">");
        assertThat(html).contains("<meta property=\"og:title\" content=\"제주 여행 - " + nick + "\">");
        assertThat(html).contains("content=\"첫날 바다를 봤다 가");
        assertThat(html).contains("<meta property=\"og:image\" content=\"http://localhost:8080/files/0b0e9c7e-1111-4222-8333-444455556666.png\">");
        String desc = html.substring(html.indexOf("og:description\" content=\"") + 25);
        desc = desc.substring(0, desc.indexOf('"'));
        assertThat(desc.codePointCount(0, desc.length())).isEqualTo(100);
    }

    @Test
    void 이미지가_없으면_사이트_기본_이미지() throws Exception {
        BlogFixture b = blog("og-noimg", "PUBLIC");
        long id = writePost(b.owner(), b.blogId(), "글", "글만");
        assertThat(page("/blog/og-noimg/posts/" + id))
                .contains("og:image\" content=\"http://localhost:8080/img/og-default.png\"");
    }

    @Test
    void 일부_공개_글은_share_값이_맞을_때만_비공개와_삭제_글은_기본() throws Exception {
        BlogFixture link = blog("og-link", "LINK");
        BlogFixture priv = blog("og-priv", "PRIVATE");
        long l = writePost(link.owner(), link.blogId(), "일부글", "c");
        long p = writePost(priv.owner(), priv.blogId(), "비밀글", "c");
        String token = getAs(link.owner(), "/api/blogs/" + link.blogId() + "/share-link").json().get("token").asText();
        assertThat(page("/blog/og-link/posts/" + l)).contains("og:title\" content=\"메인블로그\"").doesNotContain("일부글");
        assertThat(page("/blog/og-link/posts/" + l + "?share=wrong")).doesNotContain("일부글");
        assertThat(page("/blog/og-link/posts/" + l + "?share=" + token)).contains("og:title\" content=\"일부글 - ");
        assertThat(page("/blog/og-priv/posts/" + p)).doesNotContain("비밀글");
        deleteAs(link.owner(), "/api/posts/" + l);
        assertThat(page("/blog/og-link/posts/" + l + "?share=" + token)).doesNotContain("일부글");
        assertThat(page("/blog/og-link/posts/999999")).contains("og:title\" content=\"메인블로그\"");
    }

    @Test
    void 제목의_따옴표와_태그는_이스케이프한다() throws Exception {
        BlogFixture b = blog("og-esc", "PUBLIC");
        long id = writePost(b.owner(), b.blogId(), "\"><script>x</script>", "c");
        String html = page("/blog/og-esc/posts/" + id);
        assertThat(html).doesNotContain("<script>x</script>");
        assertThat(html).contains("&quot;&gt;&lt;script&gt;");
    }

    @Test
    void 주소의_블로그가_글의_블로그와_다르면_기본() throws Exception {
        BlogFixture b = blog("og-addr", "PUBLIC");
        blog("og-addr2", "PUBLIC");
        long id = writePost(b.owner(), b.blogId(), "진짜글", "c");
        assertThat(page("/blog/og-addr2/posts/" + id)).doesNotContain("진짜글");
    }
}
