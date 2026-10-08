package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 카테고리 (004 T024, US2-7, FR-021, FR-022, D-100 #8). */
class CategoryIntegrationTest extends BoardTestBase {

    private String name(String n) throws Exception {
        return json(Map.of("name", n));
    }

    @Test
    void 글_관리_권한자만_추가하고_멤버는_403() throws Exception {
        BlogFixture b = blog("cat-perm", "PUBLIC");
        Member m = memberOf(b.blogId(), "멤버");
        Member mgr = memberOf(b.blogId(), "부장");
        setManagerPerms(b.blogId(), mgr.id(), false, false, true);
        String url = "/api/blogs/" + b.blogId() + "/categories";
        assertThat(postAs(b.owner(), url, name("여행")).status()).isEqualTo(201);
        assertThat(postAs(mgr, url, name("맛집")).status()).isEqualTo(201);
        assertThat(postAs(m, url, name("일기")).status()).isEqualTo(403);
        assertThat(postAs(admin("관리"), url, name("공지")).status()).isEqualTo(403);
        JsonNode list = getAs(m, url).json();
        assertThat(list.get("categories")).extracting(c -> c.get("name").asText()).containsExactly("여행", "맛집");
        assertThat(list.get("canManage").asBoolean()).isFalse();
        assertThat(getAs(b.owner(), url).json().get("canManage").asBoolean()).isTrue();
    }

    @Test
    void 이름_31자는_400_중복은_409_30개_넘으면_409() throws Exception {
        BlogFixture b = blog("cat-rule", "PUBLIC");
        String url = "/api/blogs/" + b.blogId() + "/categories";
        assertThat(postAs(b.owner(), url, name("가".repeat(31))).status()).isEqualTo(400);
        assertThat(postAs(b.owner(), url, name(" ")).status()).isEqualTo(400);
        assertThat(postAs(b.owner(), url, name("가".repeat(30))).status()).isEqualTo(201);
        Res dup = postAs(b.owner(), url, name(" " + "가".repeat(30) + " "));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("CATEGORY_DUPLICATE");
        for (int i = 2; i <= 30; i++) {
            assertThat(postAs(b.owner(), url, name("c" + i)).status()).isEqualTo(201);
        }
        Res over = postAs(b.owner(), url, name("c31"));
        assertThat(over.status()).isEqualTo(409);
        assertThat(over.code()).isEqualTo("CATEGORY_LIMIT_EXCEEDED");
    }

    @Test
    void 이름_바꾸기_순서_바꾸기_삭제하면_글은_미분류() throws Exception {
        BlogFixture b = blog("cat-edit", "PUBLIC");
        String url = "/api/blogs/" + b.blogId() + "/categories";
        long a = postAs(b.owner(), url, name("A")).json().get("id").asLong();
        long c = postAs(b.owner(), url, name("C")).json().get("id").asLong();
        Res renamed = patchAs(b.owner(), url + "/" + a, name("B"));
        assertThat(renamed.json().get("name").asText()).isEqualTo("B");
        assertThat(patchAs(b.owner(), url + "/" + a, name("C")).status()).isEqualTo(409);
        assertThat(putAs(b.owner(), url + "/order", json(Map.of("categoryIds", List.of(c, a)))).status()).isEqualTo(204);
        assertThat(getAs(null, url).json().get("categories").get(0).get("name").asText()).isEqualTo("C");
        assertThat(putAs(b.owner(), url + "/order", json(Map.of("categoryIds", List.of(c)))).status()).isEqualTo(400);

        long post = writePost(b.owner(), b.blogId(), "분류됨", "c");
        jdbc.update("UPDATE posts SET category_id = ? WHERE id = ?", c, post);
        assertThat(getAs(null, url).json().get("categories").get(0).get("postCount").asInt()).isEqualTo(1);
        assertThat(deleteAs(b.owner(), url + "/" + c).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT category_id FROM posts WHERE id = ?", Long.class, post)).isNull();
        assertThat(getAs(null, url).json().get("uncategorizedCount").asInt()).isEqualTo(1);
        assertThat(deleteAs(b.owner(), url + "/" + c).code()).isEqualTo("CATEGORY_NOT_FOUND");
    }
}
