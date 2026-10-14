package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 블로그 안 검색: 블로그 글 목록의 q (D-136, BRD-08 검색어 규칙). */
class BlogPostSearchIntegrationTest extends BoardTestBase {

    private Res list(Member who, long blogId, String q) throws Exception {
        return getAs(who, "/api/blogs/" + blogId + "/posts?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
    }

    private static List<String> titles(JsonNode page) {
        List<String> t = new ArrayList<>();
        page.get("items").forEach(i -> t.add(i.get("title").asText()));
        return t;
    }

    @Test
    void 이_블로그_글만_제목_태그_닉네임으로_찾는다() throws Exception {
        BlogFixture b = blog("bsrch-a", "PUBLIC");
        BlogFixture other = blog("bsrch-b", "PUBLIC");
        Member writer = memberOf(b.blogId(), "산책가");
        writePost(b.owner(), b.blogId(), "제주 여행 후기", "c");
        writePost(other.owner(), other.blogId(), "부산 여행 후기", "c");
        Res w = postAs(writer, "/api/blogs/" + b.blogId() + "/posts", postJson("아무 제목", "c", null, List.of("여행")));
        assertThat(w.status()).isEqualTo(201);
        writePost(b.owner(), b.blogId(), "요리 일기", "c");

        assertThat(titles(list(null, b.blogId(), "여행").json())).containsExactlyInAnyOrder("제주 여행 후기", "아무 제목");
        assertThat(titles(list(null, b.blogId(), "산책가").json())).containsExactly("아무 제목");
        assertThat(list(null, b.blogId(), "없는말없는말").json().get("items")).isEmpty();
        assertThat(list(null, b.blogId(), "").json().get("items")).hasSize(3);
    }

    @Test
    void 비공개_블로그는_멤버만_찾고_검색어_규칙이_같다() throws Exception {
        BlogFixture b = blog("bsrch-p", "PRIVATE");
        writePost(b.owner(), b.blogId(), "비밀 여행", "c");
        assertThat(titles(list(b.owner(), b.blogId(), "여행").json())).containsExactly("비밀 여행");
        assertThat(list(null, b.blogId(), "여행").status()).isIn(401, 403, 404);
        Res bad = list(b.owner(), b.blogId(), "가");
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.code()).isEqualTo("SEARCH_KEYWORD_INVALID");
    }
}
