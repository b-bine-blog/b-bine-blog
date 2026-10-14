package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 메인 피드 태그 탭 (D-135). */
class FeedTagIntegrationTest extends BoardTestBase {

    private static List<String> names(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void 태그_탭은_정리된_이름으로_추가한_순서대로_본인에게만_보인다() throws Exception {
        Member me = member("태그회원");
        assertThat(names(putAs(me, "/api/me/feed-tags/Java", null).json())).containsExactly("java");
        assertThat(names(add(me, enc("#스프링 부트")).json()))
                .containsExactly("java", "스프링_부트");
        assertThat(names(putAs(me, "/api/me/feed-tags/java", null).json())).containsExactly("java", "스프링_부트");
        assertThat(names(getAs(member("남"), "/api/me/feed-tags").json())).isEmpty();

        assertThat(deleteAs(me, "/api/me/feed-tags/java").status()).isEqualTo(204);
        assertThat(names(getAs(me, "/api/me/feed-tags").json())).containsExactly("스프링_부트");
        assertThat(getAs(null, "/api/me/feed-tags").status()).isEqualTo(401);
    }

    @Test
    void 규칙에_맞지_않으면_400_정한_개수를_넘으면_409() throws Exception {
        Member me = member("많이");
        assertThat(add(me, enc("a-b")).status()).isEqualTo(400);
        for (int i = 1; i <= 5; i++) {
            assertThat(putAs(me, "/api/me/feed-tags/t" + i, null).status()).isEqualTo(200);
        }
        assertThat(putAs(me, "/api/me/feed-tags/t6", null).status()).isEqualTo(409);
        assertThat(putAs(me, "/api/me/feed-tags/t1", null).status()).isEqualTo(200);  // 이미 있는 것은 그대로
    }

    /** 이미 인코딩한 이름을 다시 인코딩하지 않게 URI로 보낸다. */
    private Res add(Member who, String encodedName) throws Exception {
        return call(who, put(java.net.URI.create("/api/me/feed-tags/" + encodedName)));
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
