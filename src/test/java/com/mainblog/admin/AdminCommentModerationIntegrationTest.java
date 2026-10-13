package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 관리자 댓글 숨김·삭제·복구 (005 T038, US5-5, D-103 #9·#11). */
class AdminCommentModerationIntegrationTest extends AdminTestBase {

    private long comment(Member who, long postId, String content, Long parentId) throws Exception {
        Map<String, Object> body = parentId == null ? Map.of("content", content)
                : Map.of("content", content, "parentId", parentId);
        return postAs(who, "/api/posts/" + postId + "/comments", json(body)).json().get("id").asLong();
    }

    @Test
    void 숨김_해제_삭제는_작성자에게_알리지_않고_기록이_남는다() throws Exception {
        Member admin = admin("관리자1");
        BlogFixture b = blog("cmt-mod", "PUBLIC");
        Member writer = member("댓글러1");
        long post = writePost(b.owner(), b.blogId(), "글", "본문");
        long c = comment(writer, post, "나쁜 댓글", null);
        int before = notificationsOf(writer.id()).size();

        assertThat(putAs(admin, "/api/admin/comments/" + c + "/hidden", null).status()).isEqualTo(204);
        assertThat(getAs(null, "/api/posts/" + post + "/comments").body()).doesNotContain("나쁜 댓글");
        assertThat(getAs(null, "/api/posts/" + post).json().get("commentCount").asInt()).isZero();
        assertThat(putAs(admin, "/api/admin/comments/" + c + "/hidden", null).code()).isEqualTo("ALREADY_HIDDEN");
        assertThat(deleteAs(admin, "/api/admin/comments/" + c + "/hidden").status()).isEqualTo(204);
        assertThat(getAs(null, "/api/posts/" + post + "/comments").body()).contains("나쁜 댓글");
        assertThat(deleteAs(admin, "/api/admin/comments/" + c + "/hidden").code()).isEqualTo("NOT_HIDDEN");
        assertThat(deleteAs(admin, "/api/admin/comments/" + c).status()).isEqualTo(204);
        assertThat(deleteAs(admin, "/api/admin/comments/" + c).status()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM comments WHERE id = ?", Long.class, c))
                .isEqualTo(admin.id());

        assertThat(notificationsOf(writer.id())).hasSize(before);
        assertThat(actionTypes()).containsExactly("COMMENT_HIDE", "COMMENT_UNHIDE", "COMMENT_DELETE");

        JsonNode list = getAs(admin, "/api/admin/comments?postId=" + post).json().get("items");
        assertThat(list.get(0).get("status").asText()).isEqualTo("DELETED");
        assertThat(list.get(0).get("deletedBy").asText()).isEqualTo("ADMIN");
        assertThat(list.get(0).get("content").asText()).isEqualTo("나쁜 댓글");
    }

    @Test
    void 답글이_남은_최상위_댓글을_숨기면_숨긴_자리가_남고_답글은_보인다() throws Exception {
        Member admin = admin("관리자2");
        BlogFixture b = blog("cmt-hide-top", "PUBLIC");
        Member writer = member("댓글러2");
        Member replier = member("답글러2");
        long post = writePost(b.owner(), b.blogId(), "글", "본문");
        long top = comment(writer, post, "부모 댓글", null);
        comment(replier, post, "남는 답글", top);
        long lonely = comment(writer, post, "답글 없는 댓글", null);

        putAs(admin, "/api/admin/comments/" + top + "/hidden", null);
        putAs(admin, "/api/admin/comments/" + lonely + "/hidden", null);
        Res r = getAs(null, "/api/posts/" + post + "/comments");
        JsonNode items = r.json().get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("hidden").asBoolean()).isTrue();
        assertThat(items.get(0).get("content").isNull()).isTrue();
        assertThat(items.get(0).get("author").isNull()).isTrue();
        assertThat(items.get(0).get("replies").get(0).get("content").asText()).isEqualTo("남는 답글");
        assertThat(r.body()).doesNotContain("부모 댓글").doesNotContain("답글 없는 댓글");
    }

    @Test
    void 관리자_블로그장이_지운_댓글은_복구되고_작성자가_지운_댓글은_409_30일_뒤는_404() throws Exception {
        clock.setTo(LocalDateTime.of(2026, 10, 1, 3, 0));
        Member admin = admin("관리자3");
        BlogFixture b = blog("cmt-restore", "PUBLIC");
        Member writer = member("댓글러3");
        long post = writePost(b.owner(), b.blogId(), "글", "본문");
        long byAdmin = comment(writer, post, "관리자가 지움", null);
        long byOwner = comment(writer, post, "블로그장이 지움", null);
        long bySelf = comment(writer, post, "본인이 지움", null);
        long old = comment(writer, post, "오래 지난 댓글", null);
        deleteAs(admin, "/api/admin/comments/" + byAdmin);
        deleteAs(b.owner(), "/api/comments/" + byOwner);
        deleteAs(writer, "/api/comments/" + bySelf);
        deleteAs(admin, "/api/admin/comments/" + old);

        assertThat(postAs(admin, "/api/admin/comments/" + byAdmin + "/restore", null).status()).isEqualTo(204);
        assertThat(postAs(admin, "/api/admin/comments/" + byOwner + "/restore", null).status()).isEqualTo(204);
        Res self = postAs(admin, "/api/admin/comments/" + bySelf + "/restore", null);
        assertThat(self.status()).isEqualTo(409);
        assertThat(self.code()).isEqualTo("NOT_RESTORABLE");
        assertThat(getAs(null, "/api/posts/" + post + "/comments").body()).contains("관리자가 지움", "블로그장이 지움");
        assertThat(actions("RESTORE")).hasSize(2);
        assertThat(postAs(admin, "/api/admin/comments/" + byAdmin + "/restore", null).code())
                .isEqualTo("NOT_RESTORABLE");

        clock.setTo(LocalDateTime.of(2026, 10, 31, 4, 0));
        runDaily();
        assertThat(postAs(relogin(admin), "/api/admin/comments/" + old + "/restore", null).status()).isEqualTo(404);
    }
}
