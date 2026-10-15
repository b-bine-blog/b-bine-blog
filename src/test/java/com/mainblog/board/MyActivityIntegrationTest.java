package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 계정 메뉴의 작성글·작성댓글·스크랩 (D-134). */
class MyActivityIntegrationTest extends BoardTestBase {

    private void comment(Member who, long postId, String content) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("content", content);
        m.put("parentId", null);
        assertThat(postAs(who, "/api/posts/" + postId + "/comments", json(m)).status()).isEqualTo(201);
    }

    @Test
    void 스크랩은_누르면_켜지고_다시_누르면_취소되며_본인_목록에만_보인다() throws Exception {
        BlogFixture b = blog("scrap-toggle", "PUBLIC");
        long first = writePost(b.owner(), b.blogId(), "첫 글", "c");
        long second = writePost(b.owner(), b.blogId(), "둘째 글", "c");
        Member reader = member("독자");

        assertThat(putAs(reader, "/api/posts/" + second + "/scrap", null).json().get("scrapped").asBoolean()).isTrue();
        assertThat(putAs(reader, "/api/posts/" + first + "/scrap", null).json().get("scrapped").asBoolean()).isTrue();
        assertThat(getAs(reader, "/api/posts/" + first).json().get("scrapped").asBoolean()).isTrue();

        JsonNode mine = getAs(reader, "/api/me/scraps").json();
        assertThat(mine.get("totalElements").asInt()).isEqualTo(2);
        assertThat(mine.get("items").get(0).get("title").asText()).isEqualTo("첫 글");  // 스크랩한 순서 최신부터
        assertThat(getAs(b.owner(), "/api/me/scraps").json().get("totalElements").asInt()).isZero();

        // 글 목록에서도 보는 회원이 스크랩한 글만 scrapped(카테고리 줄 책갈피 표시)
        JsonNode list = getAs(reader, "/api/blogs/" + b.blogId() + "/posts").json().get("items");
        list.forEach(item -> assertThat(item.get("scrapped").asBoolean()).isTrue());
        getAs(null, "/api/blogs/" + b.blogId() + "/posts").json().get("items")
                .forEach(item -> assertThat(item.get("scrapped").asBoolean()).isFalse());
        getAs(b.owner(), "/api/blogs/" + b.blogId() + "/posts").json().get("items")
                .forEach(item -> assertThat(item.get("scrapped").asBoolean()).isFalse());

        assertThat(putAs(reader, "/api/posts/" + first + "/scrap", null).json().get("scrapped").asBoolean()).isFalse();
        assertThat(getAs(reader, "/api/me/scraps").json().get("totalElements").asInt()).isEqualTo(1);
        assertThat(getAs(null, "/api/me/scraps").status()).isEqualTo(401);
    }

    @Test
    void 지운_글이나_비공개로_바뀐_블로그의_글은_스크랩_목록에서_빠진다() throws Exception {
        BlogFixture b = blog("scrap-hide", "PUBLIC");
        long kept = writePost(b.owner(), b.blogId(), "남는 글", "c");
        long gone = writePost(b.owner(), b.blogId(), "지울 글", "c");
        Member reader = member("독자");
        putAs(reader, "/api/posts/" + kept + "/scrap", null);
        putAs(reader, "/api/posts/" + gone + "/scrap", null);
        deleteAs(b.owner(), "/api/posts/" + gone);
        assertThat(getAs(reader, "/api/me/scraps").json().get("totalElements").asInt()).isEqualTo(1);

        jdbc.update("UPDATE blogs SET visibility = 'PRIVATE' WHERE id = ?", b.blogId());
        assertThat(getAs(reader, "/api/me/scraps").json().get("totalElements").asInt()).isZero();
        assertThat(getAs(b.owner(), "/api/me/posts").json().get("totalElements").asInt()).isEqualTo(1);  // 멤버는 본다
    }

    @Test
    void 메인_관리자는_스크랩하지_못한다() throws Exception {
        BlogFixture b = blog("scrap-admin", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        assertThat(putAs(admin("관리"), "/api/posts/" + post + "/scrap", null).status()).isEqualTo(403);
    }

    @Test
    void 작성글과_작성댓글은_내_것만_최신순으로_지운_것은_빼고() throws Exception {
        BlogFixture b = blog("my-activity", "PUBLIC");
        Member other = memberOf(b.blogId(), "다른멤버");
        long mine = writePost(b.owner(), b.blogId(), "내 글", "c");
        writePost(other, b.blogId(), "남의 글", "c");
        long deleted = writePost(b.owner(), b.blogId(), "지운 글", "c");
        deleteAs(b.owner(), "/api/posts/" + deleted);

        JsonNode posts = getAs(b.owner(), "/api/me/posts").json();
        assertThat(posts.get("totalElements").asInt()).isEqualTo(1);
        assertThat(posts.get("items").get(0).get("id").asLong()).isEqualTo(mine);

        comment(other, mine, "첫 댓글");
        comment(other, mine, "둘째 댓글");
        comment(b.owner(), mine, "주인 댓글");
        JsonNode comments = getAs(other, "/api/me/comments").json();
        assertThat(comments.get("totalElements").asInt()).isEqualTo(2);
        JsonNode top = comments.get("items").get(0);
        assertThat(top.get("content").asText()).isEqualTo("둘째 댓글");
        assertThat(top.get("postTitle").asText()).isEqualTo("내 글");
        assertThat(top.get("blogAddress").asText()).isEqualTo("my-activity");
    }

    @Test
    void 좋아요한_글은_누른_순서_최신부터_본인_목록에만_보인다() throws Exception {
        BlogFixture b = blog("likes-list", "PUBLIC");
        long first = writePost(b.owner(), b.blogId(), "첫 글", "c");
        long second = writePost(b.owner(), b.blogId(), "둘째 글", "c");
        Member reader = member("좋아요독자");
        jdbc.update("INSERT INTO post_likes (user_id, post_id) VALUES (?, ?)", reader.id(), second);
        jdbc.update("INSERT INTO post_likes (user_id, post_id) VALUES (?, ?)", reader.id(), first);

        JsonNode mine = getAs(reader, "/api/me/likes").json();
        assertThat(mine.get("totalElements").asInt()).isEqualTo(2);
        assertThat(mine.get("items").get(0).get("title").asText()).isEqualTo("첫 글");
        assertThat(getAs(b.owner(), "/api/me/likes").json().get("totalElements").asInt()).isZero();
        deleteAs(b.owner(), "/api/posts/" + first);
        assertThat(getAs(reader, "/api/me/likes").json().get("totalElements").asInt()).isEqualTo(1);
        assertThat(getAs(null, "/api/me/likes").status()).isEqualTo(401);
    }
}
