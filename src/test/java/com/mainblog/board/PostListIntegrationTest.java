package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 블로그 글 목록 (004 T023, US2-1~6, FR-017~FR-021, D-100 #7, D-133). */
class PostListIntegrationTest extends BoardTestBase {

    @Test
    void 글_35개를_10_20_30개씩_나누고_50은_400() throws Exception {
        BlogFixture b = blog("list-page", "PUBLIC");
        for (int i = 1; i <= 35; i++) {
            insertPost(b.blogId(), b.owner().id(), "글" + i, "c", clock.now().plusMinutes(i));
        }
        String url = "/api/blogs/" + b.blogId() + "/posts";
        assertThat(getAs(null, url).json().get("totalPages").asInt()).isEqualTo(4);
        assertThat(getAs(null, url + "?size=20").json().get("totalPages").asInt()).isEqualTo(2);
        JsonNode p2 = getAs(null, url + "?size=30&page=2").json();
        assertThat(p2.get("totalPages").asInt()).isEqualTo(2);
        assertThat(p2.get("items")).hasSize(5);
        assertThat(p2.get("totalElements").asLong()).isEqualTo(35);
        assertThat(getAs(null, url + "?size=50").status()).isEqualTo(400);
        JsonNode p3 = getAs(null, url + "?page=3").json();
        assertThat(p3.get("items").get(0).get("title").asText()).isEqualTo("글15");
        assertThat(getAs(null, url).json().get("items").get(0).get("title").asText()).isEqualTo("글35");
    }

    @Test
    void 첫_이미지와_카테고리_필터_미분류_삭제_글_제외() throws Exception {
        BlogFixture b = blog("list-filter", "PUBLIC");
        long cat = insertCategory(b.blogId(), "여행");
        long p1 = writePost(b.owner(), b.blogId(), "그림", "앞 ![a](https://img.example/a.png) ![b](https://img.example/b.png)");
        jdbc.update("UPDATE posts SET category_id = ? WHERE id = ?", cat, p1);
        writePost(b.owner(), b.blogId(), "그냥", "글만");
        long gone = writePost(b.owner(), b.blogId(), "지움", "c");
        deleteAs(b.owner(), "/api/posts/" + gone);

        String url = "/api/blogs/" + b.blogId() + "/posts";
        JsonNode all = getAs(null, url).json();
        assertThat(all.get("totalElements").asInt()).isEqualTo(2);
        JsonNode byCat = getAs(null, url + "?categoryId=" + cat).json().get("items");
        assertThat(byCat).hasSize(1);
        assertThat(byCat.get(0).get("thumbnailUrl").asText()).isEqualTo("https://img.example/a.png");
        assertThat(byCat.get(0).get("categoryName").asText()).isEqualTo("여행");
        JsonNode none = getAs(null, url + "?categoryId=0").json().get("items");
        assertThat(none).hasSize(1);
        assertThat(none.get(0).get("title").asText()).isEqualTo("그냥");
        assertThat(none.get(0).get("thumbnailUrl").isNull()).isTrue();
    }

    @Test
    void 고친_글만_수정됨_표시_조회수는_상관없음() throws Exception {
        BlogFixture b = blog("list-edited", "PUBLIC");
        long edited = writePost(b.owner(), b.blogId(), "고침", "처음");
        writePost(b.owner(), b.blogId(), "그대로", "c");
        assertThat(putAs(b.owner(), "/api/posts/" + edited, postJson("고침", "다시", null, List.of())).status())
                .isEqualTo(200);
        jdbc.update("UPDATE posts SET view_count = view_count + 1");  // 조회수가 늘어도 수정됨이 아니다(D-133)

        JsonNode items = getAs(null, "/api/blogs/" + b.blogId() + "/posts").json().get("items");
        assertThat(items.get(0).get("title").asText()).isEqualTo("그대로");
        assertThat(items.get(0).get("edited").asBoolean()).isFalse();
        assertThat(items.get(1).get("edited").asBoolean()).isTrue();
    }

    @Test
    void 볼_수_없는_블로그의_목록은_404() throws Exception {
        BlogFixture b = blog("list-priv", "PRIVATE");
        assertThat(getAs(member("밖"), "/api/blogs/" + b.blogId() + "/posts").status()).isEqualTo(404);
        assertThat(getAs(b.owner(), "/api/blogs/" + b.blogId() + "/posts").status()).isEqualTo(200);
    }
}
