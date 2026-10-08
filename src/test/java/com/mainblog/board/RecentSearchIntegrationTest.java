package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 최근 검색어 (004 T041, US6-8·9, FR-059, D-100 #17). */
class RecentSearchIntegrationTest extends BoardTestBase {

    private void search(Member who, String q) throws Exception {
        assertThat(getAs(who, "/api/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)).status()).isEqualTo(200);
    }

    private List<String> keywords(Member who) throws Exception {
        List<String> k = new ArrayList<>();
        getAs(who, "/api/me/recent-searches").json().forEach(n -> k.add(n.get("keyword").asText()));
        return k;
    }

    @Test
    void 열한_개를_검색하면_최근_10개_같은_말은_맨_앞으로() throws Exception {
        Member me = member("검색자");
        for (int i = 1; i <= 11; i++) {
            search(me, "검색" + i);
            clock.advance(java.time.Duration.ofSeconds(1));
        }
        List<String> k = keywords(me);
        assertThat(k).hasSize(10).first().isEqualTo("검색11");
        assertThat(k).doesNotContain("검색1");
        search(me, "검색5");
        assertThat(keywords(me)).hasSize(10).first().isEqualTo("검색5");
        assertThat(keywords(me).stream().filter("검색5"::equals)).hasSize(1);
    }

    @Test
    void 하나_삭제_전체_삭제_남의_것은_404_비회원은_저장_안_함() throws Exception {
        Member me = member("나");
        Member other = member("남");
        search(me, "하나둘");
        search(me, "셋넷");
        search(null, "비회원검색");
        JsonNode list = getAs(me, "/api/me/recent-searches").json();
        long id = list.get(0).get("id").asLong();
        assertThat(deleteAs(other, "/api/me/recent-searches/" + id).status()).isEqualTo(404);
        assertThat(keywords(other)).isEmpty();
        assertThat(deleteAs(me, "/api/me/recent-searches/" + id).status()).isEqualTo(204);
        assertThat(keywords(me)).hasSize(1);
        assertThat(deleteAs(me, "/api/me/recent-searches").status()).isEqualTo(204);
        assertThat(keywords(me)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recent_searches", Integer.class)).isZero();
        assertThat(getAs(null, "/api/me/recent-searches").status()).isEqualTo(401);
    }
}
