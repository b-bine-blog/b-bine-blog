package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** 본문 속 글 주소 카드 (D-138). */
class PostCardIntegrationTest extends BoardTestBase {

    @Test
    void 볼_수_있는_글은_요약을_주고_조회수는_세지_않는다() throws Exception {
        BlogFixture b = blog("card-pub", "PUBLIC");
        long id = writePost(b.owner(), b.blogId(), "카드 글", "c");
        JsonNode card = getAs(null, "/api/posts/" + id + "/card").json();
        assertThat(card.get("title").asText()).isEqualTo("카드 글");
        assertThat(card.get("blog").get("address").asText()).isEqualTo("card-pub");
        Integer views = jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, id);
        assertThat(views).isZero();
    }

    @Test
    void 볼_수_없는_글은_카드를_주지_않는다() throws Exception {
        BlogFixture b = blog("card-priv", "PRIVATE");
        long id = writePost(b.owner(), b.blogId(), "비밀 글", "c");
        assertThat(getAs(null, "/api/posts/" + id + "/card").status()).isIn(401, 403, 404);
        assertThat(getAs(b.owner(), "/api/posts/" + id + "/card").status()).isEqualTo(200);
        assertThat(getAs(null, "/api/posts/999999/card").status()).isEqualTo(404);
    }
}
