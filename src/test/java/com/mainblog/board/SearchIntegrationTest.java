package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 통합 검색 (004 T040, US6-1~7, FR-052~FR-058, SC-007, D-100 #16). */
class SearchIntegrationTest extends BoardTestBase {

    private Res search(Member who, String q, String extra) throws Exception {
        return getAs(who, "/api/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8) + extra);
    }

    private static List<String> titles(JsonNode page) {
        List<String> t = new ArrayList<>();
        page.get("items").forEach(i -> t.add(i.get("title").asText()));
        return t;
    }

    @Test
    void 전체_검색은_블로그와_글을_함께_주고_공개된_것만_센다() throws Exception {
        BlogFixture pub = blog("srch-pub", "PUBLIC");
        jdbc.update("UPDATE blogs SET name = '여행 블로그' WHERE id = ?", pub.blogId());
        BlogFixture link = blog("srch-link", "LINK");
        BlogFixture priv = blog("srch-priv", "PRIVATE");
        writePost(pub.owner(), pub.blogId(), "제주 여행 후기", "c");
        writePost(link.owner(), link.blogId(), "일부 여행", "c");
        writePost(priv.owner(), priv.blogId(), "비공개 여행", "c");
        long deleted = writePost(pub.owner(), pub.blogId(), "지운 여행", "c");
        deleteAs(pub.owner(), "/api/posts/" + deleted);

        JsonNode r = search(null, "여행", "").json();
        assertThat(r.get("type").asText()).isEqualTo("ALL");
        assertThat(titles(r.get("posts"))).containsExactly("제주 여행 후기");
        assertThat(r.get("posts").get("totalElements").asInt()).isEqualTo(1);
        assertThat(r.get("blogs").get("items")).hasSize(1);
        assertThat(r.get("blogs").get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void 태그와_닉네임은_정확히_일치하고_탈퇴_회원은_빠진다() throws Exception {
        BlogFixture b = blog("srch-tag", "PUBLIC");
        Member writer = memberOf(b.blogId(), "여행가");
        Res w = postAs(writer, "/api/blogs/" + b.blogId() + "/posts", postJson("아무 제목", "c", null, List.of("맛집")));
        assertThat(w.status()).isEqualTo(201);
        writePost(b.owner(), b.blogId(), "맛집탐방", "c");
        assertThat(titles(search(null, "맛집", "&type=TAG").json().get("posts"))).containsExactly("아무 제목");
        assertThat(titles(search(null, "#맛집", "&type=TAG").json().get("posts"))).containsExactly("아무 제목");
        assertThat(titles(search(null, "여행가", "&type=NICKNAME").json().get("posts"))).containsExactly("아무 제목");
        assertThat(search(null, "여행", "&type=NICKNAME").json().get("posts").get("items")).isEmpty();
        jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", writer.id());
        assertThat(search(null, "여행가", "&type=NICKNAME").json().get("posts").get("items")).isEmpty();
    }

    @Test
    void 검색어_규칙에_맞지_않으면_400() throws Exception {
        for (String q : new String[] {"가", "가".repeat(21), "   ", "!!@@"}) {
            Res r = search(null, q, "");
            assertThat(r.status()).as(q).isEqualTo(400);
            assertThat(r.code()).isEqualTo("SEARCH_KEYWORD_INVALID");
        }
        assertThat(getAs(null, "/api/search").code()).isEqualTo("SEARCH_KEYWORD_INVALID");
        assertThat(search(null, "여행", "&type=WRONG").status()).isEqualTo(400);
        assertThat(search(null, "없는말없는말", "&type=TITLE").json().get("posts").get("items")).isEmpty();
    }

    @Test
    void 최신순과_인기순_인기는_같으면_최신() throws Exception {
        BlogFixture b = blog("srch-sort", "PUBLIC");
        long a = insertPost(b.blogId(), b.owner().id(), "여행 A", "c", clock.now().minusHours(3));
        long c = insertPost(b.blogId(), b.owner().id(), "여행 C", "c", clock.now().minusHours(2));
        long d = insertPost(b.blogId(), b.owner().id(), "여행 D", "c", clock.now().minusHours(1));
        setLikes(a, 5);
        setLikes(c, 2);
        setLikes(d, 2);
        assertThat(titles(search(null, "여행", "&type=TITLE&sort=LATEST").json().get("posts")))
                .containsExactly("여행 D", "여행 C", "여행 A");
        assertThat(titles(search(null, "여행", "&type=TITLE&sort=POPULAR").json().get("posts")))
                .containsExactly("여행 A", "여행 D", "여행 C");
        assertThat(search(null, "여행", "&type=TITLE").json().get("posts").get("items")).hasSize(3);
    }

    @Test
    void 결과_35개는_페이지로_나누고_차단한_작성자의_글은_개수에서도_빠진다() throws Exception {
        BlogFixture b = blog("srch-page", "PUBLIC");
        Member blocked = memberOf(b.blogId(), "차단될");
        for (int i = 1; i <= 35; i++) {
            insertPost(b.blogId(), b.owner().id(), "여행 " + i, "c", clock.now().plusMinutes(i));
        }
        insertPost(b.blogId(), blocked.id(), "여행 차단", "c", clock.now());
        Member reader = member("독자");
        block(reader.id(), blocked.id());
        JsonNode page = search(reader, "여행", "&type=TITLE&size=10&page=4").json().get("posts");
        assertThat(page.get("totalElements").asInt()).isEqualTo(35);
        assertThat(page.get("totalPages").asInt()).isEqualTo(4);
        assertThat(page.get("items")).hasSize(5);
        assertThat(search(null, "여행", "&type=TITLE").json().get("posts").get("totalElements").asInt()).isEqualTo(36);
    }
}
