package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 댓글·답글 (004 T035, US5, FR-036~FR-045, D-87, D-100 #1~#3·#9~#11·#13). */
class CommentIntegrationTest extends BoardTestBase {

    private String comment(String content, Long parentId) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("content", content);
        m.put("parentId", parentId);
        return json(m);
    }

    private long write(Member who, long postId, String content, Long parentId) throws Exception {
        Res r = postAs(who, "/api/posts/" + postId + "/comments", comment(content, parentId));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.json().get("id").asLong();
    }

    private JsonNode list(Member who, long postId) throws Exception {
        return getAs(who, "/api/posts/" + postId + "/comments").json();
    }

    @Test
    void 글자_수와_쓸_수_있는_사람() throws Exception {
        BlogFixture b = blog("cmt-rule", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        String url = "/api/posts/" + post + "/comments";
        Member outsider = member("밖사람");
        assertThat(postAs(outsider, url, comment("가", null)).status()).isEqualTo(201);
        assertThat(postAs(outsider, url, comment("가".repeat(500), null)).status()).isEqualTo(201);
        assertThat(postAs(outsider, url, comment("가".repeat(501), null)).status()).isEqualTo(400);
        assertThat(postAs(outsider, url, comment("  ", null)).status()).isEqualTo(400);
        assertThat(postAs(null, url, comment("x", null)).status()).isEqualTo(401);
        assertThat(postAs(admin("관리"), url, comment("x", null)).code()).isEqualTo("ADMIN_NOT_ALLOWED");
    }

    @Test
    void 답글은_한_단계이고_답글의_답글은_대상이_바뀌며_닉네임은_지금_것() throws Exception {
        BlogFixture b = blog("cmt-reply", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member a = member("에이");
        Member c = member("씨");
        long top = write(a, post, "첫 댓글", null);
        long r1 = write(c, post, "답글", top);
        long r2 = write(a, post, "답글의 답글", r1);
        jdbc.update("UPDATE users SET nickname = '새씨' WHERE id = ?", c.id());

        JsonNode items = list(null, post).get("items");
        assertThat(items).hasSize(1);
        JsonNode replies = items.get(0).get("replies");
        assertThat(replies).hasSize(2);
        assertThat(replies.get(0).get("parentId").asLong()).isEqualTo(top);
        assertThat(replies.get(0).get("replyToNickname").asText()).isEqualTo("에이");
        assertThat(replies.get(1).get("id").asLong()).isEqualTo(r2);
        assertThat(replies.get(1).get("parentId").asLong()).isEqualTo(top);
        assertThat(replies.get(1).get("replyToNickname").asText()).isEqualTo("새씨");
        assertThat(replies.get(0).get("author").get("nickname").asText()).isEqualTo("새씨");
    }

    @Test
    void 수정은_작성자만_수정됨_표시_삭제는_작성자와_글_관리_권한자() throws Exception {
        BlogFixture b = blog("cmt-edit", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member a = member("에이");
        Member other = member("남");
        long id = write(a, post, "원래", null);
        Res edited = patchAs(a, "/api/comments/" + id, json(Map.of("content", "고침")));
        assertThat(edited.json().get("edited").asBoolean()).isTrue();
        assertThat(edited.json().get("content").asText()).isEqualTo("고침");
        assertThat(patchAs(other, "/api/comments/" + id, json(Map.of("content", "x"))).code())
                .isEqualTo("COMMENT_FORBIDDEN");
        assertThat(patchAs(b.owner(), "/api/comments/" + id, json(Map.of("content", "x"))).status()).isEqualTo(403);
        assertThat(deleteAs(other, "/api/comments/" + id).status()).isEqualTo(403);
        long id2 = write(a, post, "둘", null);
        assertThat(deleteAs(b.owner(), "/api/comments/" + id).status()).isEqualTo(204);
        Member mgr = memberOf(b.blogId(), "부장");
        setManagerPerms(b.blogId(), mgr.id(), false, false, true);
        assertThat(deleteAs(mgr, "/api/comments/" + id2).status()).isEqualTo(204);
        assertThat(list(null, post).get("items")).isEmpty();
    }

    @Test
    void 답글이_있는_댓글을_지우면_삭제된_자리로_남고_답글이_모두_지워지면_사라진다() throws Exception {
        BlogFixture b = blog("cmt-del", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member a = member("에이");
        Member c = member("씨");
        long top = write(a, post, "부모", null);
        long reply = write(c, post, "답글", top);
        deleteAs(a, "/api/comments/" + top);
        JsonNode first = list(null, post).get("items").get(0);
        assertThat(first.get("deleted").asBoolean()).isTrue();
        assertThat(first.get("content").isNull()).isTrue();
        assertThat(first.get("author").isNull()).isTrue();
        assertThat(first.get("replies")).hasSize(1);
        deleteAs(c, "/api/comments/" + reply);
        assertThat(list(null, post).get("items")).isEmpty();
        assertThat(list(null, post).get("totalElements").asInt()).isZero();
    }

    @Test
    void 탈퇴한_회원의_댓글은_탈퇴한_회원_블로그를_떠나도_닉네임_그대로() throws Exception {
        BlogFixture b = blog("cmt-author", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member gone = member("탈퇴자");
        Member left = memberOf(b.blogId(), "떠난이");
        write(gone, post, "x", null);
        write(left, post, "y", null);
        jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", gone.id());
        deleteAs(left, "/api/blogs/" + b.blogId() + "/members/me");
        JsonNode items = list(null, post).get("items");
        assertThat(items.get(0).get("author").get("nickname").asText()).isEqualTo("탈퇴한 회원");
        assertThat(items.get(1).get("author").get("nickname").asText()).isEqualTo("떠난이");
    }

    @Test
    void 차단한_회원의_댓글은_숨기고_같은_블로그_멤버끼리는_보인다() throws Exception {
        BlogFixture b = blog("cmt-block", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member reader = member("독자");
        Member blocked = member("차단됨");
        write(blocked, post, "보이면 안 됨", null);
        block(reader.id(), blocked.id());
        assertThat(list(reader, post).get("items")).isEmpty();
        assertThat(list(null, post).get("items")).hasSize(1);
        addMember(b.blogId(), reader.id(), "MEMBER");
        addMember(b.blogId(), blocked.id(), "MEMBER");
        assertThat(list(reader, post).get("items")).hasSize(1);
    }

    @Test
    void 알림은_글_작성자와_답글_대상에게_자기_것과_꺼진_설정은_없음() throws Exception {
        BlogFixture b = blog("cmt-noti", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member a = member("에이");
        Member c = member("씨");
        write(b.owner(), post, "내 글에 내 댓글", null);
        long top = write(a, post, "댓글", null);
        write(c, post, "답글", top);
        write(a, post, "내 댓글에 내 답글", top);
        assertThat(notificationsOf(b.owner().id())).containsExactly("POST_COMMENT");
        assertThat(notificationsOf(a.id())).containsExactly("COMMENT_REPLY");
        setNotification(b.owner().id(), "post_comment", false);
        write(c, post, "또 댓글", null);
        assertThat(notificationsOf(b.owner().id())).hasSize(1);
    }

    @Test
    void 공지에는_댓글을_달_수_없다() throws Exception {
        Member adm = admin("관리자");
        long notice = postAs(adm, "/api/notices", postJson("공지", "내용", null, null)).json().get("id").asLong();
        Res r = postAs(member("회원"), "/api/posts/" + notice + "/comments", comment("x", null));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("COMMENT_NOT_ALLOWED");
    }
}
