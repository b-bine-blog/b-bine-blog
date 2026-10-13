package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 관리자 글 숨김·삭제·복구 (005 T037, US5-1~4, FR-015~FR-019, D-103 #9~#11). */
class AdminPostModerationIntegrationTest extends AdminTestBase {

    private String status(long postId) {
        return jdbc.queryForObject("SELECT status FROM posts WHERE id = ?", String.class, postId);
    }

    @Test
    void 숨기면_목록_검색_피드에서_빠지고_작성자에게만_숨긴_글로_보인다() throws Exception {
        Member admin = admin("관리자1");
        BlogFixture b = blog("mod-hide", "PUBLIC");
        Member author = memberOf(b.blogId(), "작성자1");
        Member reader = member("독자1");
        long post = writePost(author, b.blogId(), "숨길글제목", "본문");

        assertThat(putAs(admin, "/api/admin/posts/" + post + "/hidden", null).status()).isEqualTo(204);
        assertThat(status(post)).isEqualTo("HIDDEN");
        assertThat(getAs(null, "/api/blogs/" + b.blogId() + "/posts").body()).doesNotContain("숨길글제목");
        assertThat(getAs(null, "/api/feed").body()).doesNotContain("숨길글제목");
        assertThat(getAs(null, "/api/search?q=" + java.net.URLEncoder.encode("숨길글제목", "UTF-8")).body())
                .doesNotContain("숨길글제목");

        Res mine = getAs(author, "/api/posts/" + post);
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.json().get("hiddenByAdmin").asBoolean()).isTrue();
        assertThat(mine.json().get("canEdit").asBoolean()).isFalse();
        assertThat(mine.json().get("canComment").asBoolean()).isFalse();
        assertThat(mine.json().get("shareable").asBoolean()).isFalse();
        assertThat(putAs(author, "/api/posts/" + post, postJson("고침", "본문", null, List.of())).status())
                .isIn(403, 404);
        assertThat(postAs(author, "/api/posts/" + post + "/comments", json(Map.of("content", "댓글"))).status())
                .isIn(403, 404);
        assertThat(putAs(author, "/api/posts/" + post + "/like", null).status()).isIn(403, 404);
        assertThat(getAs(b.owner(), "/api/posts/" + post).status()).isEqualTo(404);
        assertThat(getAs(reader, "/api/posts/" + post).status()).isEqualTo(404);
        assertThat(getAs(null, "/api/posts/" + post).status()).isEqualTo(404);
        assertThat(deleteAs(b.owner(), "/api/posts/" + post).status()).isEqualTo(404);

        assertThat(notificationsOf(author.id())).containsExactly("POST_HIDDEN");
        assertThat(jdbc.queryForObject("SELECT tab FROM notifications WHERE notification_type = 'POST_HIDDEN'",
                String.class)).isEqualTo("OPERATION");
        assertThat(actionTypes()).containsExactly("POST_HIDE");
        assertThat(putAs(admin, "/api/admin/posts/" + post + "/hidden", null).code()).isEqualTo("ALREADY_HIDDEN");

        // 30일 정리 뒤에도 남는다
        clock.setTo(clock.now().plusDays(40));
        runDaily();
        assertThat(status(post)).isEqualTo("HIDDEN");

        admin = relogin(admin);
        assertThat(deleteAs(admin, "/api/admin/posts/" + post + "/hidden").status()).isEqualTo(204);
        assertThat(getAs(null, "/api/posts/" + post).status()).isEqualTo(200);
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND created_at >= ?", author.id(),
                clock.now())).isZero(); // 해제는 알리지 않는다
        assertThat(actionTypes()).containsExactly("POST_HIDE", "POST_UNHIDE");
        assertThat(deleteAs(admin, "/api/admin/posts/" + post + "/hidden").code()).isEqualTo("NOT_HIDDEN");
    }

    @Test
    void 삭제는_알림과_기록이_남고_004_경로로_지워도_기록된다_관리자_수정은_403() throws Exception {
        Member admin = admin("관리자2");
        BlogFixture b = blog("mod-delete", "PUBLIC");
        Member author = memberOf(b.blogId(), "작성자2");
        String img = uploadOne(author, b.blogId());
        long p1 = writePost(author, b.blogId(), "지울글1", "![a](" + img + ")");
        long p2 = writePost(author, b.blogId(), "지울글2", "본문");

        assertThat(putAs(admin, "/api/posts/" + p1, postJson("관리자 고침", "본문", null, List.of())).status())
                .isEqualTo(403);
        assertThat(deleteAs(admin, "/api/admin/posts/" + p1).status()).isEqualTo(204);
        assertThat(getAs(author, "/api/posts/" + p1).status()).isEqualTo(404);
        assertThat(getAs(author, "/api/blogs/" + b.blogId() + "/posts").body()).doesNotContain(img);
        assertThat(deleteAs(admin, "/api/posts/" + p2).status()).isEqualTo(204);
        assertThat(notificationsOf(author.id())).containsExactly("POST_DELETED", "POST_DELETED");
        assertThat(actionTypes()).containsExactly("POST_DELETE", "POST_DELETE");
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM posts WHERE id = ?", Long.class, p2)).isEqualTo(admin.id());
        assertThat(deleteAs(admin, "/api/admin/posts/" + p1).status()).isEqualTo(404);
    }

    @Test
    void 관리자_블로그장이_지운_글은_30일_안에_복구되고_사진도_보인다() throws Exception {
        clock.setTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        Member admin = admin("관리자3");
        BlogFixture b = blog("mod-restore", "PUBLIC");
        Member author = memberOf(b.blogId(), "작성자3");
        String img = uploadOne(author, b.blogId());
        long post = writePost(author, b.blogId(), "복구할글", "![a](" + img + ")");
        long byOwner = writePost(author, b.blogId(), "블로그장이지운글", "본문");
        deleteAs(admin, "/api/admin/posts/" + post);
        deleteAs(b.owner(), "/api/posts/" + byOwner);

        clock.setTo(LocalDateTime.of(2026, 10, 20, 10, 0));
        admin = relogin(admin);
        JsonNode detail = getAs(admin, "/api/admin/posts/" + post).json();
        assertThat(detail.get("deletedBy").asText()).isEqualTo("ADMIN");
        assertThat(detail.get("restorable").asBoolean()).isTrue();
        assertThat(getAs(admin, "/api/admin/posts/" + byOwner).json().get("deletedBy").asText())
                .isEqualTo("BLOG_MANAGER");

        assertThat(postAs(admin, "/api/admin/posts/" + post + "/restore", null).status()).isEqualTo(204);
        assertThat(postAs(admin, "/api/admin/posts/" + byOwner + "/restore", null).status()).isEqualTo(204);
        Res seen = getAs(null, "/api/posts/" + post);
        assertThat(seen.status()).isEqualTo(200);
        assertThat(seen.json().get("contentHtml").asText()).contains(img);
        assertThat(count("SELECT COUNT(*) FROM images WHERE owner_id = ? AND deleted_at IS NULL", post)).isEqualTo(1);
        assertThat(notificationsOf(author.id())).contains("POST_RESTORED");
        assertThat(actions("RESTORE")).hasSize(2);
        assertThat(postAs(admin, "/api/admin/posts/" + post + "/restore", null).code()).isEqualTo("NOT_RESTORABLE");

        // 복구 뒤 다시 지우면 알림이 다시 간다
        long before = notificationsOf(author.id()).stream().filter("POST_DELETED"::equals).count();
        clock.advance(java.time.Duration.ofMinutes(1));
        assertThat(deleteAs(relogin(admin), "/api/admin/posts/" + post).status()).isEqualTo(204);
        assertThat(notificationsOf(author.id()).stream().filter("POST_DELETED"::equals).count())
                .isEqualTo(before + 1);
    }

    @Test
    void 숨긴_채_지운_글은_숨김으로_돌아가고_작성자가_지운_글과_30일_지난_글과_폐쇄_블로그_글은_복구하지_않는다()
            throws Exception {
        clock.setTo(LocalDateTime.of(2026, 10, 1, 3, 0));
        Member admin = admin("관리자4");
        BlogFixture b = blog("mod-norestore", "PUBLIC");
        Member author = memberOf(b.blogId(), "작성자4");
        long hidden = writePost(author, b.blogId(), "숨긴채지움", "본문");
        long self = writePost(author, b.blogId(), "작성자가지움", "본문");
        long old = writePost(author, b.blogId(), "오래된글", "본문");
        putAs(admin, "/api/admin/posts/" + hidden + "/hidden", null);
        deleteAs(admin, "/api/admin/posts/" + hidden);
        deleteAs(author, "/api/posts/" + self);
        deleteAs(admin, "/api/admin/posts/" + old);

        assertThat(postAs(admin, "/api/admin/posts/" + hidden + "/restore", null).status()).isEqualTo(204);
        assertThat(status(hidden)).isEqualTo("HIDDEN");
        Res selfRestore = postAs(admin, "/api/admin/posts/" + self + "/restore", null);
        assertThat(selfRestore.status()).isEqualTo(409);
        assertThat(selfRestore.code()).isEqualTo("NOT_RESTORABLE");
        assertThat(getAs(admin, "/api/admin/posts/" + self).json().get("deletedBy").asText()).isEqualTo("AUTHOR");

        clock.setTo(LocalDateTime.of(2026, 10, 31, 4, 0));
        runDaily();
        assertThat(postAs(relogin(admin), "/api/admin/posts/" + old + "/restore", null).status()).isEqualTo(404);

        BlogFixture closed = blog("mod-closed", "PUBLIC");
        long inClosed = writePost(closed.owner(), closed.blogId(), "폐쇄블로그글", "본문");
        Member a = relogin(admin);
        deleteAs(a, "/api/admin/posts/" + inClosed);
        jdbc.update("UPDATE blogs SET status = 'CLOSED', closed_at = ? WHERE id = ?", clock.now(), closed.blogId());
        assertThat(postAs(a, "/api/admin/posts/" + inClosed + "/restore", null).code())
                .isEqualTo("BLOG_ALREADY_CLOSED");
    }

    @Test
    void 관리자_글_목록과_상세는_비공개_숨김_블로그_글도_보인다() throws Exception {
        Member admin = admin("관리자5");
        BlogFixture priv = blog("mod-list-priv", "PRIVATE");
        BlogFixture hiddenBlog = blog("mod-list-hidden", "PUBLIC");
        long p1 = writePost(priv.owner(), priv.blogId(), "비공개블로그글", "**본문**");
        long p2 = writePost(hiddenBlog.owner(), hiddenBlog.blogId(), "숨김블로그글", "본문");
        putAs(admin, "/api/admin/blogs/" + hiddenBlog.blogId() + "/hidden", null);
        deleteAs(admin, "/api/admin/posts/" + p2);

        JsonNode all = getAs(admin, "/api/admin/posts").json();
        assertThat(all.get("items").findValuesAsText("title")).contains("비공개블로그글", "숨김블로그글");
        JsonNode deleted = getAs(admin, "/api/admin/posts?status=DELETED").json().get("items");
        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).get("deletedBy").asText()).isEqualTo("ADMIN");
        assertThat(deleted.get(0).get("restorable").asBoolean()).isTrue();
        assertThat(getAs(admin, "/api/admin/posts?blogId=" + priv.blogId()).json().get("totalElements").asInt())
                .isEqualTo(1);
        assertThat(getAs(admin, "/api/admin/posts?q=" + java.net.URLEncoder.encode("비공개", "UTF-8")).json()
                .get("totalElements").asInt()).isEqualTo(1);
        assertThat(getAs(admin, "/api/admin/posts?status=WRONG").status()).isEqualTo(400);

        JsonNode d = getAs(admin, "/api/admin/posts/" + p1).json();
        assertThat(d.get("content").asText()).isEqualTo("**본문**");
        assertThat(d.get("status").asText()).isEqualTo("VISIBLE");
        assertThat(d.get("deletedBy").isNull()).isTrue();
        assertThat(getAs(admin, "/api/admin/posts/999999").status()).isEqualTo(404);
    }
}
