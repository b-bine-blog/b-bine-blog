package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.common.storage.LocalDiskFileStorage;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 게시판 04:00 정리 (004 T054, FR-013, SC-012, D-100 #4, research R-11). */
class BoardDailyTasksIntegrationTest extends BoardTestBase {

    @Autowired
    LocalDiskFileStorage storage;

    @Test
    void 삭제_30일_지난_글은_댓글_좋아요_조회와_함께_지우고_이미지는_삭제_표시() throws Exception {
        BlogFixture b = blog("daily-post", "PUBLIC");
        String img = uploadOne(b.owner(), b.blogId());
        long post = writePost(b.owner(), b.blogId(), "글", "![a](" + img + ")");
        Member fan = member("팬");
        long top = postAs(fan, "/api/posts/" + post + "/comments", json(Map.of("content", "댓글"))).json().get("id").asLong();
        postAs(fan, "/api/posts/" + post + "/comments", json(Map.of("content", "답글", "parentId", top)));
        putAs(fan, "/api/posts/" + post + "/like", null);
        getAs(fan, "/api/posts/" + post);
        deleteAs(b.owner(), "/api/posts/" + post);

        clock.advance(Duration.ofDays(29));
        runDaily();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, post)).isEqualTo(1);
        clock.advance(Duration.ofDays(2));
        runDaily();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, post)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_likes", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_views", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM images WHERE stored_name = ?", Object.class,
                img.substring(7))).isNotNull();
        clock.advance(Duration.ofDays(31));
        runDaily();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images", Integer.class)).isZero();
        assertThat(Files.exists(storage.root().resolve(img.substring(7)))).isFalse();
    }

    @Test
    void 연결_안_된_이미지는_하루_뒤_삭제_표시_답글_남은_댓글은_답글_정리_뒤에() throws Exception {
        BlogFixture b = blog("daily-orphan", "PUBLIC");
        String orphan = uploadOne(b.owner(), b.blogId());
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member a = member("에이");
        long top = postAs(a, "/api/posts/" + post + "/comments", json(Map.of("content", "부모"))).json().get("id").asLong();
        long reply = postAs(a, "/api/posts/" + post + "/comments", json(Map.of("content", "답", "parentId", top)))
                .json().get("id").asLong();
        deleteAs(a, "/api/comments/" + top);
        clock.advance(Duration.ofDays(10));
        deleteAs(relogin(a), "/api/comments/" + reply);

        clock.advance(Duration.ofDays(21));
        runDaily();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM images WHERE stored_name = ?", Object.class,
                orphan.substring(7))).isNotNull();
        // 부모는 31일 지났지만 답글이 아직 21일이라 남는다
        assertThat(jdbc.queryForList("SELECT id FROM comments", Long.class)).containsExactlyInAnyOrder(top, reply);
        clock.advance(Duration.ofDays(10));
        runDaily();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments", Integer.class)).isZero();
    }

    @Test
    void 일주일_지난_조회_기록은_지운다() throws Exception {
        BlogFixture b = blog("daily-view", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        getAs(member("독자"), "/api/posts/" + post);
        clock.advance(Duration.ofDays(8));
        runDaily();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_views", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, post)).isEqualTo(1);
    }
}
