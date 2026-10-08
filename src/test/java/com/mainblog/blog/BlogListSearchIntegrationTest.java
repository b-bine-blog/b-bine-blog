package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** US2 목록·검색 (T029, FR-014, FR-015, D-89, SC-007). */
class BlogListSearchIntegrationTest extends BlogTestBase {

    private List<String> addresses(Res r) throws Exception {
        List<String> result = new ArrayList<>();
        for (JsonNode item : r.json().get("items")) {
            result.add(item.get("address").asText());
        }
        return result;
    }

    @Test
    void 목록에는_공개_블로그만_보이고_숨김은_빠진다() throws Exception {
        Member m = member("목록");
        createBlog(m, "pub-only", "PUBLIC", "FREE");
        createBlog(m, "link-no", "LINK", "FREE");
        createBlog(m, "priv-no", "PRIVATE", "FREE");
        long hidden = createBlog(member("숨김"), "hidden-no", "PUBLIC", "FREE");
        jdbc.update("UPDATE blogs SET hidden = TRUE WHERE id = ?", hidden);
        Res r = getAs(null, "/api/blogs");
        assertThat(r.status()).isEqualTo(200);
        assertThat(addresses(r)).containsExactly("pub-only");
        assertThat(r.json().get("size").asInt()).isEqualTo(10);
    }

    @Test
    void 인기순은_멤버_수가_많은_순_같으면_최신순() throws Exception {
        Member m = member("인기");
        long x = createBlog(m, "blog-x", "PUBLIC", "FREE");
        clock.advance(java.time.Duration.ofMinutes(1));
        long y = createBlog(member("인기2"), "blog-y", "PUBLIC", "FREE");
        clock.advance(java.time.Duration.ofMinutes(1));
        long z = createBlog(member("인기3"), "blog-z", "PUBLIC", "FREE");
        jdbc.update("UPDATE blogs SET member_count = 10 WHERE id = ?", x);
        jdbc.update("UPDATE blogs SET member_count = 4 WHERE id IN (?, ?)", y, z);
        // created_at은 DB가 채우므로 순서를 직접 정한다
        jdbc.update("UPDATE blogs SET created_at = ? WHERE id = ?", BASE_TIME, y);
        jdbc.update("UPDATE blogs SET created_at = ? WHERE id = ?", BASE_TIME.plusHours(1), z);
        assertThat(addresses(getAs(null, "/api/blogs?sort=popular"))).containsExactly("blog-x", "blog-z", "blog-y");
    }

    @Test
    void 페이지_크기는_10_20_30만() throws Exception {
        assertThat(getAs(null, "/api/blogs?size=20").status()).isEqualTo(200);
        assertThat(getAs(null, "/api/blogs?size=15").status()).isEqualTo(400);
        assertThat(getAs(null, "/api/blogs?page=0").status()).isEqualTo(400);
    }

    @Test
    void 번호_페이지로_나뉜다() throws Exception {
        for (int i = 0; i < 12; i++) {
            Member m = member("페이지" + i);
            createBlog(m, "page-" + i, "PUBLIC", "FREE");
        }
        Res p2 = getAs(null, "/api/blogs?page=2&size=10");
        assertThat(p2.json().get("totalCount").asInt()).isEqualTo(12);
        assertThat(p2.json().get("items").size()).isEqualTo(2);
    }

    @Test
    void 검색은_이름_소개_태그로_찾고_공개만_나온다() throws Exception {
        Member m = member("검색");
        postAs(m, "/api/blogs", "{\"name\":\"여행\",\"address\":\"jeju-pub\",\"visibility\":\"PUBLIC\","
                + "\"joinPolicy\":\"FREE\",\"description\":\"제주에서 살아요\"}");
        postAs(m, "/api/blogs", "{\"name\":\"비밀\",\"address\":\"jeju-priv\",\"visibility\":\"PRIVATE\","
                + "\"joinPolicy\":\"FREE\",\"description\":\"제주 비밀 기록\"}");
        postAs(m, "/api/blogs", "{\"name\":\"요리\",\"address\":\"cook-tag\",\"visibility\":\"PUBLIC\","
                + "\"joinPolicy\":\"FREE\",\"tags\":[\"제주\"]}");
        Res r = getAs(null, "/api/blogs/search?q=" + java.net.URLEncoder.encode("제주", "UTF-8"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(addresses(r)).containsExactlyInAnyOrder("jeju-pub", "cook-tag");
        Res none = getAs(null, "/api/blogs/search?q=" + java.net.URLEncoder.encode("없는말", "UTF-8"));
        assertThat(none.json().get("items").size()).isZero();
    }

    @Test
    void 검색어는_2에서_20자이고_특수문자만이면_안_된다() throws Exception {
        assertThat(getAs(null, "/api/blogs/search?q=a").status()).isEqualTo(400);
        assertThat(getAs(null, "/api/blogs/search?q=" + "a".repeat(21)).status()).isEqualTo(400);
        assertThat(getAs(null, "/api/blogs/search?q=%2B%2B%2B").status()).isEqualTo(400);
        assertThat(getAs(null, "/api/blogs/search?q=%20%20").status()).isEqualTo(400);
        assertThat(getAs(null, "/api/blogs/search?q=ab%2B").status()).isEqualTo(200);
    }
}
