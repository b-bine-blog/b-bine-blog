package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 글 링크 미리보기 (004 T048, US9, FR-047~FR-050, SC-010, D-100 #5·#6·#14, D-110). */
class OgTagIntegrationTest extends BoardTestBase {

    private String page(String path) throws Exception {
        Res r = getAs(null, path);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.raw().getContentType()).startsWith("text/html");
        return r.body();
    }

    @Test
    void 공개_글은_주소_제목_닉네임_요약_미리보기_그림을_채운다() throws Exception {
        BlogFixture b = blog("og-pub", "PUBLIC");
        String nick = jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, b.owner().id());
        long id = writePost(b.owner(), b.blogId(), "제주 여행",
                "# 첫날\n\n**바다**를 봤다 " + "가".repeat(120) + "\n\n![a](/files/0b0e9c7e-1111-4222-8333-444455556666.png)");
        String html = page("/blog/og-pub/posts/" + id);
        assertThat(html).contains("<meta property=\"og:url\" content=\"http://localhost:8080/blog/og-pub/posts/" + id + "\">");
        assertThat(html).contains("<meta property=\"og:title\" content=\"제주 여행 - " + nick + "\">");
        assertThat(html).contains("content=\"첫날 바다를 봤다 가");
        assertThat(html).contains("<meta property=\"og:image\" content=\"http://localhost:8080/og/blog/og-pub/posts/" + id + ".png\">");
        String desc = html.substring(html.indexOf("og:description\" content=\"") + 25);
        desc = desc.substring(0, desc.indexOf('"'));
        assertThat(desc.codePointCount(0, desc.length())).isEqualTo(100);
    }

    @Test
    void 미리보기_그림은_글_화면_모양의_PNG다() throws Exception {
        BlogFixture b = blog("og-card", "PUBLIC");
        long id = writePost(b.owner(), b.blogId(), "아주 긴 제목 ".repeat(3).strip(), "본문 ".repeat(80));
        Res r = getAs(null, "/og/blog/og-card/posts/" + id + ".png");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.raw().getContentType()).isEqualTo("image/png");
        assertThat(r.raw().getHeader("Cache-Control")).isEqualTo("max-age=3600");
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(
                new java.io.ByteArrayInputStream(r.raw().getContentAsByteArray()));
        assertThat(img.getWidth()).isEqualTo(1200);
        assertThat(img.getHeight()).isEqualTo(630);
    }

    @Test
    void 공지도_미리보기_그림을_쓴다() throws Exception {
        long id = postAs(admin("관리자"), "/api/notices", postJson("점검 안내", "내일 점검합니다", null, null)).json().get("id").asLong();
        assertThat(page("/notices/" + id)).contains("og:image\" content=\"http://localhost:8080/og/notices/" + id + ".png\"");
        assertThat(getAs(null, "/og/notices/" + id + ".png").status()).isEqualTo(200);
    }

    @Test
    void 일부_공개_글은_share_값이_맞을_때만_비공개와_삭제_글은_기본() throws Exception {
        BlogFixture link = blog("og-link", "LINK");
        BlogFixture priv = blog("og-priv", "PRIVATE");
        long l = writePost(link.owner(), link.blogId(), "일부글", "c");
        long p = writePost(priv.owner(), priv.blogId(), "비밀글", "c");
        String token = getAs(link.owner(), "/api/blogs/" + link.blogId() + "/share-link").json().get("token").asText();
        assertThat(page("/blog/og-link/posts/" + l)).contains("og:title\" content=\"b-bine blog\"")
                .contains("og:description\" content=\"비공개글입니다\"").doesNotContain("일부글").doesNotContain("og:image");
        assertThat(page("/blog/og-link/posts/" + l + "?share=wrong")).doesNotContain("일부글");
        assertThat(page("/blog/og-link/posts/" + l + "?share=" + token)).contains("og:title\" content=\"일부글 - ")
                .contains("og:image\" content=\"http://localhost:8080/og/blog/og-link/posts/" + l + ".png?share=" + token + "\"");
        assertThat(getAs(null, "/og/blog/og-link/posts/" + l + ".png?share=" + token).status()).isEqualTo(200);
        assertThat(getAs(null, "/og/blog/og-link/posts/" + l + ".png").status()).isEqualTo(404);
        assertThat(getAs(null, "/og/blog/og-priv/posts/" + p + ".png").status()).isEqualTo(404);
        assertThat(page("/blog/og-priv/posts/" + p)).doesNotContain("비밀글").contains("비공개글입니다")
                .doesNotContain("og:image").contains("twitter:card\" content=\"summary\"");
        deleteAs(link.owner(), "/api/posts/" + l);
        assertThat(page("/blog/og-link/posts/" + l + "?share=" + token)).doesNotContain("일부글");
        assertThat(page("/blog/og-link/posts/999999")).contains("og:title\" content=\"b-bine blog\"");
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
