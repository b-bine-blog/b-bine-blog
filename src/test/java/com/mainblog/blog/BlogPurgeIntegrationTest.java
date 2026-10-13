package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** US5 폐쇄 30일 뒤 정리 (T047, BLG-09, FR-008, FR-081, D-35, D-86, SC-009). */
class BlogPurgeIntegrationTest extends BlogTestBase {

    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 10, 9, 4, 0);

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void 폐쇄_30일_뒤_내용과_주소를_지우고_블로그_행과_기록은_남긴다() throws Exception {
        Member owner = member("정리장");
        long blog = createBlog(owner, "travel-log", "LINK", "FREE");
        patchAs(owner, "/api/blogs/" + blog, "{\"tags\":[\"제주\"]}");
        Member writer = member("작성자");
        addMember(blog, writer.id(), "MEMBER");

        jdbc.update("INSERT INTO categories (blog_id, name) VALUES (?, '여행')", blog);
        long category = jdbc.queryForObject("SELECT MAX(id) FROM categories", Long.class);
        jdbc.update("INSERT INTO posts (blog_id, author_id, category_id, title, content) VALUES (?, ?, ?, '제목', '본문')",
                blog, writer.id(), category);
        long post = jdbc.queryForObject("SELECT MAX(id) FROM posts", Long.class);
        jdbc.update("INSERT INTO comments (author_id, post_id, content) VALUES (?, ?, '댓글')", owner.id(), post);
        long comment = jdbc.queryForObject("SELECT MAX(id) FROM comments", Long.class);
        jdbc.update("INSERT INTO comments (parent_id, author_id, post_id, content) VALUES (?, ?, ?, '대댓글')",
                comment, writer.id(), post);
        jdbc.update("INSERT INTO post_likes (user_id, post_id) VALUES (?, ?)", owner.id(), post);
        jdbc.update("INSERT INTO images (uploader_id, owner_type, owner_id, stored_name, original_name, content_type, "
                + "size_bytes) VALUES (?, 'POST', ?, 'post-img-1', 'a.png', 'png', 10)", writer.id(), post);
        jdbc.update("INSERT INTO images (uploader_id, owner_type, owner_id, stored_name, original_name, content_type, "
                + "size_bytes) VALUES (?, 'BLOG_COVER', ?, 'cover-img-1', 'c.png', 'png', 10)", owner.id(), blog);
        Member kicked = member("강퇴");
        long blacklist = insertBlacklist(blog, kicked.id(), owner.id());
        jdbc.update("INSERT INTO blacklist_appeals (blacklist_id, user_id, name_matched, phone_matched, status) "
                + "VALUES (?, ?, TRUE, TRUE, 'REJECTED')", blacklist, kicked.id());
        insertBlogReport(blog, writer.id(), "POST", post);

        jdbc.update("UPDATE blogs SET status = 'CLOSED', closed_at = ? WHERE id = ?", CLOSED_AT, blog);

        clock.setTo(CLOSED_AT.plusDays(29));
        runDaily();
        assertThat(count("SELECT COUNT(*) FROM posts WHERE blog_id = ?", blog)).isEqualTo(1);
        Member other = member("다른");
        Res taken = postAs(other, "/api/blogs", blogJson("새 블로그", "travel-log", "PUBLIC", "FREE"));
        assertThat(taken.status()).isEqualTo(409);
        assertThat(taken.code()).isEqualTo("BLOG_ADDRESS_TAKEN");

        clock.setTo(CLOSED_AT.plusDays(30).plusMinutes(1));
        runDaily();
        assertThat(count("SELECT COUNT(*) FROM posts WHERE blog_id = ?", blog)).isZero();
        assertThat(count("SELECT COUNT(*) FROM comments")).isZero();
        assertThat(count("SELECT COUNT(*) FROM post_likes")).isZero();
        assertThat(count("SELECT COUNT(*) FROM images WHERE owner_type IN ('POST', 'BLOG_COVER')")).isZero();
        assertThat(count("SELECT COUNT(*) FROM categories WHERE blog_id = ?", blog)).isZero();
        assertThat(count("SELECT COUNT(*) FROM blog_blacklists WHERE blog_id = ?", blog)).isZero();
        assertThat(count("SELECT COUNT(*) FROM blacklist_appeals")).isZero();
        assertThat(count("SELECT COUNT(*) FROM blog_share_links WHERE blog_id = ?", blog)).isZero();
        assertThat(count("SELECT COUNT(*) FROM blog_tags WHERE blog_id = ?", blog)).isZero();
        // 블로그 행·멤버·제재·신고 기록은 남는다(D-86)
        assertThat(jdbc.queryForObject("SELECT address FROM blogs WHERE id = ?", String.class, blog)).isNull();
        assertThat(count("SELECT COUNT(*) FROM blog_members WHERE blog_id = ?", blog)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM blog_sanctions WHERE blog_id = ?", blog)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM reports WHERE blog_id = ?", blog)).isEqualTo(1);

        Member reuse = relogin(other);
        assertThat(postAs(reuse, "/api/blogs", blogJson("새 블로그", "travel-log", "PUBLIC", "FREE")).status())
                .isEqualTo(201);
        runDaily(); // 다시 돌아도 새 블로그는 건드리지 않는다
        assertThat(getAs(null, "/api/blogs/address/travel-log").json().get("name").asText()).isEqualTo("새 블로그");
    }

    @Test
    void 바꾼_대표_이미지는_30일_뒤_지운다() throws Exception {
        Member owner = member("표지장");
        long blog = createBlog(owner, "cover-purge", "PUBLIC", "FREE");
        jdbc.update("INSERT INTO images (uploader_id, owner_type, owner_id, stored_name, original_name, content_type, "
                + "size_bytes, deleted_at) VALUES (?, 'BLOG_COVER', ?, 'old-cover', 'c.png', 'png', 10, ?)",
                owner.id(), blog, clock.now());
        clock.advance(java.time.Duration.ofDays(29));
        runDaily();
        assertThat(count("SELECT COUNT(*) FROM images WHERE stored_name = 'old-cover'")).isEqualTo(1);
        clock.advance(java.time.Duration.ofDays(2));
        runDaily();
        assertThat(count("SELECT COUNT(*) FROM images WHERE stored_name = 'old-cover'")).isZero();
    }
}
