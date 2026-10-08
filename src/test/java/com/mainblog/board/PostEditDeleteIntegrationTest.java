package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 글 수정·삭제 권한 (004 T016, US1-7~10, SC-004, FR-008~FR-013, D-100 #1). */
class PostEditDeleteIntegrationTest extends BoardTestBase {

    @Test
    void 작성자만_고칠_수_있고_블로그장과_관리자도_403() throws Exception {
        BlogFixture b = blog("edit-perm", "PUBLIC");
        Member m = memberOf(b.blogId(), "작성자");
        long id = writePost(m, b.blogId(), "원래", "c");
        String body = postJson("고침", "새 본문", null, List.of("태그"));
        Res ok = putAs(m, "/api/posts/" + id, body);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.json().get("title").asText()).isEqualTo("고침");
        assertThat(ok.json().get("tags").get(0).asText()).isEqualTo("태그");
        for (Member who : List.of(b.owner(), admin("관리자1"), memberOf(b.blogId(), "다른이"))) {
            Res r = putAs(who, "/api/posts/" + id, body);
            assertThat(r.status()).isEqualTo(403);
            assertThat(r.code()).isEqualTo("POST_FORBIDDEN");
        }
        assertThat(putAs(null, "/api/posts/" + id, body).status()).isEqualTo(401);
    }

    @Test
    void 다른_멤버는_지울_수_없고_블로그장은_지우며_작성자에게_알림() throws Exception {
        BlogFixture b = blog("del-owner", "PUBLIC");
        Member m = memberOf(b.blogId(), "작성자");
        long id = writePost(m, b.blogId(), "지울 글", "c");
        Res other = deleteAs(memberOf(b.blogId(), "옆사람"), "/api/posts/" + id);
        assertThat(other.status()).isEqualTo(403);
        assertThat(other.code()).isEqualTo("POST_FORBIDDEN");
        assertThat(deleteAs(b.owner(), "/api/posts/" + id).status()).isEqualTo(204);
        assertThat(notificationsOf(m.id())).containsExactly("POST_DELETED");
        assertThat(jdbc.queryForObject("SELECT status FROM posts WHERE id = ?", String.class, id)).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM posts WHERE id = ?", Long.class, id))
                .isEqualTo(b.owner().id());
    }

    @Test
    void 글_관리_권한_부블로그장과_관리자는_지울_수_있다() throws Exception {
        BlogFixture b = blog("del-mgr", "PRIVATE");
        Member m = memberOf(b.blogId(), "작성자");
        Member mgr = memberOf(b.blogId(), "부장");
        setManagerPerms(b.blogId(), mgr.id(), false, false, true);
        long p1 = writePost(m, b.blogId(), "1", "c");
        long p2 = writePost(m, b.blogId(), "2", "c");
        assertThat(deleteAs(mgr, "/api/posts/" + p1).status()).isEqualTo(204);
        assertThat(deleteAs(admin("관리자2"), "/api/posts/" + p2).status()).isEqualTo(204);
        assertThat(notificationsOf(m.id())).containsExactly("POST_DELETED", "POST_DELETED");
    }

    @Test
    void 본인이_지우면_알림이_없고_다른_블로그의_블로그장은_403() throws Exception {
        BlogFixture b = blog("del-self", "PUBLIC");
        BlogFixture other = blog("del-self2", "PUBLIC");
        Member m = memberOf(b.blogId(), "작성자");
        long p1 = writePost(m, b.blogId(), "1", "c");
        long p2 = writePost(m, b.blogId(), "2", "c");
        assertThat(deleteAs(other.owner(), "/api/posts/" + p1).status()).isEqualTo(403);
        assertThat(deleteAs(m, "/api/posts/" + p2).status()).isEqualTo(204);
        assertThat(notificationsOf(m.id())).isEmpty();
    }

    @Test
    void 블로그를_떠난_작성자는_고치거나_지울_수_없다() throws Exception {
        BlogFixture b = blog("del-left", "PUBLIC");
        Member m = memberOf(b.blogId(), "떠날이");
        long id = writePost(m, b.blogId(), "t", "c");
        deleteAs(m, "/api/blogs/" + b.blogId() + "/members/me");
        assertThat(putAs(m, "/api/posts/" + id, postJson("x", "y", null, null)).status()).isEqualTo(403);
        assertThat(deleteAs(m, "/api/posts/" + id).status()).isEqualTo(403);
        assertThat(deleteAs(b.owner(), "/api/posts/" + id).status()).isEqualTo(204);
    }
}
