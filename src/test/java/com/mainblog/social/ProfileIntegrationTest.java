package com.mainblog.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 회원 프로필과 소개 (003 T017, US3, SC-010, FR-001~FR-005, D-102 #6~#8). */
class ProfileIntegrationTest extends SocialTestBase {

    private List<String> addresses(JsonNode blogs) {
        List<String> out = new ArrayList<>();
        blogs.forEach(b -> out.add(b.get("address").asText()));
        return out;
    }

    @Test
    void 운영_참여_블로그와_팔로워_수를_비회원도_보고_다른_사람에게는_공개_블로그만() throws Exception {
        Member p = member("프로필주인");
        createBlog(p, "prof-own1", "PUBLIC", "FREE");
        createBlog(p, "prof-own2", "PUBLIC", "FREE");
        createBlog(p, "prof-link", "LINK", "FREE");
        createBlog(p, "prof-priv", "PRIVATE", "FREE");
        long hidden = createOwnedBlog(p.id(), "prof-hidden", "ACTIVE"); // 공개 블로그 개수 제한(3) 밖에서 직접 넣는다
        jdbc.update("UPDATE blogs SET hidden = TRUE WHERE id = ?", hidden);
        long closed = createOwnedBlog(p.id(), "prof-closed", "ACTIVE");
        jdbc.update("UPDATE blogs SET status = 'CLOSED', closed_at = ? WHERE id = ?", clock.now(), closed);
        long joined = createBlog(member("남의장"), "prof-joined", "PUBLIC", "FREE");
        addMember(joined, p.id(), "MEMBER");
        for (int i = 0; i < 3; i++) {
            follow(member("팔로워" + i).id(), p.id());
        }

        Res guest = getAs(null, "/api/users/" + p.id() + "/profile");
        assertThat(guest.status()).isEqualTo(200);
        JsonNode g = guest.json();
        assertThat(g.get("nickname").asText()).isEqualTo("프로필주인");
        assertThat(addresses(g.get("ownedBlogs"))).containsExactly("prof-own1", "prof-own2");
        assertThat(addresses(g.get("joinedBlogs"))).containsExactly("prof-joined");
        assertThat(g.get("followerCount").asInt()).isEqualTo(3);
        assertThat(g.get("viewer").isNull()).isTrue();

        JsonNode mine = getAs(p, "/api/users/" + p.id() + "/profile").json();
        assertThat(addresses(mine.get("ownedBlogs"))).containsExactly("prof-own1", "prof-own2", "prof-link",
                "prof-priv", "prof-hidden");
        assertThat(mine.get("viewer").get("self").asBoolean()).isTrue();

        Member other = member("구경꾼");
        JsonNode seen = getAs(other, "/api/users/" + p.id() + "/profile").json();
        assertThat(addresses(seen.get("ownedBlogs"))).containsExactly("prof-own1", "prof-own2");
    }

    @Test
    void 응답에_이메일_이름_전화번호가_없다() throws Exception {
        Member p = member("가림");
        jdbc.update("UPDATE users SET bio = '안녕하세요' WHERE id = ?", p.id());
        java.util.Map<String, Object> row = jdbc.queryForMap("SELECT email, name, phone FROM users WHERE id = ?", p.id());
        String body = getAs(member("보는이"), "/api/users/" + p.id() + "/profile").body();
        assertThat(body).contains("안녕하세요").doesNotContain((String) row.get("email"))
                .doesNotContain((String) row.get("name")).doesNotContain((String) row.get("phone"))
                .doesNotContain("\"email\"").doesNotContain("\"phone\"").doesNotContain("\"name\":\"" + row.get("name"));
    }

    @Test
    void 탈퇴_회원_관리자_없는_번호는_404() throws Exception {
        Member gone = member("떠남");
        withdraw(gone.id());
        Member adm = admin("관리자1");
        assertThat(getAs(null, "/api/users/" + gone.id() + "/profile").code()).isEqualTo("NOT_FOUND");
        assertThat(getAs(null, "/api/users/" + adm.id() + "/profile").code()).isEqualTo("NOT_FOUND");
        assertThat(getAs(null, "/api/users/999999/profile").status()).isEqualTo(404);
    }

    @Test
    void viewer는_팔로우_차단_여부만_있고_상대가_나를_차단했는지는_없다() throws Exception {
        Member a = member("에이");
        Member b = member("비");
        follow(a.id(), b.id());
        block(a.id(), b.id());
        JsonNode v = getAs(a, "/api/users/" + b.id() + "/profile").json().get("viewer");
        assertThat(v.get("self").asBoolean()).isFalse();
        assertThat(v.get("following").asBoolean()).isTrue();
        assertThat(v.get("blocking").asBoolean()).isTrue();
        JsonNode other = getAs(b, "/api/users/" + a.id() + "/profile").json().get("viewer");
        assertThat(other.get("blocking").asBoolean()).isFalse();
        assertThat(other.size()).isEqualTo(3);
        assertThat(getAs(b, "/api/users/" + a.id() + "/profile").body()).doesNotContain("blocked");
    }

    @Test
    void 소개는_0자와_500자는_되고_501자는_400_줄바꿈_유지_공백_제거_빈_값이면_null() throws Exception {
        Member m = member("소개");
        String five = "가".repeat(499) + "😀";
        Res ok = putAs(m, "/api/members/me/bio", json(java.util.Map.of("bio", five)));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.json().get("bio").asText()).isEqualTo(five);
        Res tooLong = putAs(m, "/api/members/me/bio", json(java.util.Map.of("bio", "가".repeat(501))));
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(tooLong.json().get("fieldErrors").get(0).get("field").asText()).isEqualTo("bio");

        Res lines = putAs(m, "/api/members/me/bio", json(java.util.Map.of("bio", "  첫 줄\n둘째 줄  ")));
        assertThat(lines.json().get("bio").asText()).isEqualTo("첫 줄\n둘째 줄");
        assertThat(getAs(null, "/api/users/" + m.id() + "/profile").json().get("bio").asText())
                .isEqualTo("첫 줄\n둘째 줄");
        assertThat(getAs(m, "/api/members/me").json().get("bio").asText()).isEqualTo("첫 줄\n둘째 줄");

        Res empty = putAs(m, "/api/members/me/bio", json(java.util.Map.of("bio", "   ")));
        assertThat(empty.json().get("bio").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id = ?", String.class, m.id())).isNull();
        assertThat(putAs(m, "/api/members/me/bio", "{\"bio\":null}").status()).isEqualTo(200);
    }

    @Test
    void 소개는_본인만_고치고_비회원은_401() throws Exception {
        Member m = member("본인");
        Member other = member("다른이");
        assertThat(putAs(null, "/api/members/me/bio", "{\"bio\":\"x\"}").status()).isEqualTo(401);
        putAs(other, "/api/members/me/bio", "{\"bio\":\"다른이 소개\"}");
        assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id = ?", String.class, m.id())).isNull();
        assertThat(putAs(other, "/api/users/" + m.id() + "/bio", "{\"bio\":\"x\"}").status()).isIn(401, 403, 404, 405);
    }

    @Test
    void 프로필_화면_주소는_정적_화면으로_간다() throws Exception {
        Member m = member("화면");
        Res page = getAs(null, "/profile/" + m.id());
        assertThat(page.status()).isEqualTo(200);
        assertThat(page.raw().getForwardedUrl()).isEqualTo("/profile.html");
    }
}
