package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 글 태그와 태그 글 목록 (004 T042, US7, FR-023~FR-028, SC-009). */
class TagPostIntegrationTest extends BoardTestBase {

    @Test
    void 태그는_정리되고_중복은_하나로() throws Exception {
        BlogFixture b = blog("tag-norm", "PUBLIC");
        Res r = postAs(b.owner(), "/api/blogs/" + b.blogId() + "/posts",
                postJson("t", "c", null, List.of("#맛집", " 서울 ", "Java", "제주 여행", "java")));
        assertThat(r.json().get("tags")).extracting(JsonNode::asText).containsExactly("맛집", "서울", "java", "제주_여행");
    }

    @Test
    void 열한_개_21자_특수문자는_400() throws Exception {
        BlogFixture b = blog("tag-bad", "PUBLIC");
        String url = "/api/blogs/" + b.blogId() + "/posts";
        assertThat(postAs(b.owner(), url, postJson("t", "c", null, List.of("가".repeat(21)))).status()).isEqualTo(400);
        assertThat(postAs(b.owner(), url, postJson("t", "c", null, List.of("c++"))).status()).isEqualTo(400);
        assertThat(postAs(b.owner(), url, postJson("t", "c", null, List.of("가".repeat(20)))).status()).isEqualTo(201);
    }

    @Test
    void 태그_글_목록은_공개_블로그의_보이는_글만() throws Exception {
        BlogFixture pub = blog("tag-pub", "PUBLIC");
        BlogFixture priv = blog("tag-priv", "PRIVATE");
        BlogFixture link = blog("tag-link", "LINK");
        postAs(pub.owner(), "/api/blogs/" + pub.blogId() + "/posts", postJson("공개", "c", null, List.of("제주")));
        long gone = postAs(pub.owner(), "/api/blogs/" + pub.blogId() + "/posts",
                postJson("삭제", "c", null, List.of("제주"))).json().get("id").asLong();
        deleteAs(pub.owner(), "/api/posts/" + gone);
        postAs(priv.owner(), "/api/blogs/" + priv.blogId() + "/posts", postJson("비공개", "c", null, List.of("제주")));
        postAs(link.owner(), "/api/blogs/" + link.blogId() + "/posts", postJson("일부", "c", null, List.of("제주")));
        JsonNode r = getAs(null, "/api/tags/" + URLEncoder.encode("#제주", StandardCharsets.UTF_8) + "/posts").json();
        assertThat(r.get("items")).hasSize(1);
        assertThat(r.get("items").get(0).get("title").asText()).isEqualTo("공개");
        assertThat(getAs(null, "/api/tags/" + URLEncoder.encode("c++", StandardCharsets.UTF_8) + "/posts").status())
                .isEqualTo(400);
    }
}
