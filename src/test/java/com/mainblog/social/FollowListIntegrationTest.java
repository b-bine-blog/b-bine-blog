package com.mainblog.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 팔로워·팔로잉 목록 (003 T038, US7, SOC-02, FR-015, D-22, D-102 #9). */
class FollowListIntegrationTest extends SocialTestBase {

    private List<Long> ids(JsonNode page) {
        List<Long> out = new ArrayList<>();
        page.get("items").forEach(i -> out.add(i.get("id").asLong()));
        return out;
    }

    @Test
    void 최근_팔로우_순이고_닉네임과_사진만_담는다() throws Exception {
        Member p = member("주인공");
        Member a = member("에이");
        Member b = member("비");
        Member c = member("씨");
        follow(a.id(), p.id(), clock.now().minusMinutes(2));
        follow(b.id(), p.id(), clock.now().minusMinutes(1));
        follow(p.id(), c.id(), clock.now());

        Res followers = getAs(null, "/api/users/" + p.id() + "/followers");
        assertThat(followers.status()).isEqualTo(200); // 비회원도 본다
        assertThat(ids(followers.json())).containsExactly(b.id(), a.id());
        assertThat(followers.json().get("totalElements").asLong()).isEqualTo(2);
        JsonNode first = followers.json().get("items").get(0);
        assertThat(first.get("nickname").asText()).isEqualTo("비");
        assertThat(first.has("profileImageUrl")).isTrue();
        assertThat(followers.body()).doesNotContain("email").doesNotContain("@test.com").doesNotContain("phone")
                .doesNotContain("\"name\"");
        assertThat(ids(getAs(c, "/api/users/" + p.id() + "/following").json())).containsExactly(c.id());

        // 언팔로우하면 빠진다
        assertThat(deleteAs(a, "/api/users/" + p.id() + "/follow").status()).isEqualTo(200);
        assertThat(ids(getAs(p, "/api/users/" + p.id() + "/followers").json())).containsExactly(b.id());
    }

    @Test
    void 스물다섯_명이면_20명과_5명_두_페이지() throws Exception {
        Member p = member("인기인");
        for (int i = 0; i < 25; i++) {
            long fan = createUser("fan" + i + "@test.com", PW, "팬" + i);
            follow(fan, p.id(), clock.now().minusMinutes(25 - i));
        }
        JsonNode page1 = getAs(null, "/api/users/" + p.id() + "/followers").json();
        assertThat(page1.get("items").size()).isEqualTo(20);
        assertThat(page1.get("page").asInt()).isEqualTo(1);
        assertThat(page1.get("size").asInt()).isEqualTo(20);
        assertThat(page1.get("totalElements").asLong()).isEqualTo(25);
        assertThat(page1.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page1.get("items").get(0).get("nickname").asText()).isEqualTo("팬24");
        JsonNode page2 = getAs(null, "/api/users/" + p.id() + "/followers?page=2").json();
        assertThat(page2.get("items").size()).isEqualTo(5);
        assertThat(page2.get("items").get(4).get("nickname").asText()).isEqualTo("팬0");
        assertThat(getAs(null, "/api/users/" + p.id() + "/followers?page=0").status()).isEqualTo(400);
    }

    @Test
    void 탈퇴_회원은_안_나오고_없는_회원은_404() throws Exception {
        Member p = member("주인공");
        Member gone = member("떠남");
        Member stay = member("남음");
        follow(gone.id(), p.id(), clock.now().minusMinutes(1));
        follow(stay.id(), p.id(), clock.now());
        withdraw(gone.id());

        JsonNode page = getAs(null, "/api/users/" + p.id() + "/followers").json();
        assertThat(ids(page)).containsExactly(stay.id());
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(getAs(null, "/api/users/999999/followers").status()).isEqualTo(404);
        assertThat(getAs(null, "/api/users/999999/following").status()).isEqualTo(404);
        assertThat(getAs(null, "/api/users/" + gone.id() + "/following").status()).isEqualTo(404);
    }
}
