package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** US7 멤버 제재와 블랙리스트 (T059, BLG-11, BLG-13, FR-048~FR-059). */
class SanctionIntegrationTest extends BlogTestBase {

    private Res sanction(Member actor, long blog, long target, String body) throws Exception {
        return postAs(actor, "/api/blogs/" + blog + "/members/" + target + "/sanctions", body);
    }

    private static String suspend(String period) {
        return "{\"type\":\"SUSPEND\",\"period\":\"" + period + "\",\"reason\":\"도배\"}";
    }

    @Test
    void 삼일_정지면_그_블로그만_못_들어가고_기간과_사유를_안내한다() throws Exception {
        Member owner = member("장");
        long a = createBlog(owner, "sanc-a", "PUBLIC", "FREE");
        long b = createBlog(member("비장"), "sanc-b", "PUBLIC", "FREE");
        Member m = member("정지");
        addMember(a, m.id(), "MEMBER");
        addMember(b, m.id(), "MEMBER");

        Res r = sanction(owner, a, m.id(), suspend("DAYS_3"));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().get("endsAt").asText()).startsWith(clock.now().plusDays(3).toLocalDate().toString());
        assertThat(notificationsOf(m.id())).containsExactly("MEMBER_SUSPENDED");

        Res blocked = getAs(m, "/api/blogs/address/sanc-a");
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.code()).isEqualTo("BLOG_SUSPENDED");
        assertThat(blocked.json().get("reason").asText()).isEqualTo("도배");
        assertThat(blocked.json().has("suspendedUntil")).isTrue();
        assertThat(getAs(m, "/api/blogs/address/sanc-b").status()).isEqualTo(200);
        assertThat(sanction(owner, a, m.id(), suspend("DAYS_14")).code()).isEqualTo("ALREADY_SUSPENDED");

        clock.advance(Duration.ofDays(3).plusMinutes(1));
        assertThat(getAs(relogin(m), "/api/blogs/address/sanc-a").status()).isEqualTo(200);
    }

    @Test
    void 정지_기간은_네_가지만_받는다() throws Exception {
        Member owner = member("장2");
        long a = createBlog(owner, "sanc-period", "PUBLIC", "FREE");
        Member m = member("기간");
        addMember(a, m.id(), "MEMBER");
        assertThat(sanction(owner, a, m.id(), suspend("DAYS_7")).status()).isEqualTo(400);
        assertThat(sanction(owner, a, m.id(), "{\"type\":\"SUSPEND\"}").status()).isEqualTo(400);
        assertThat(sanction(owner, a, m.id(), "{\"type\":\"BAN\"}").status()).isEqualTo(400);
        Res forever = sanction(owner, a, m.id(), suspend("PERMANENT"));
        assertThat(forever.status()).isEqualTo(201);
        assertThat(forever.json().get("endsAt").isNull()).isTrue();
    }

    @Test
    void 해제하면_바로_들어가고_해제_알림을_받는다() throws Exception {
        Member owner = member("장3");
        long a = createBlog(owner, "sanc-release", "PUBLIC", "FREE");
        Member m = member("해제");
        addMember(a, m.id(), "MEMBER");
        assertThat(sanction(owner, a, m.id(), "{\"type\":\"UNSUSPEND\"}").code()).isEqualTo("NOT_SUSPENDED");
        sanction(owner, a, m.id(), suspend("DAYS_14"));
        assertThat(sanction(owner, a, m.id(), "{\"type\":\"UNSUSPEND\"}").status()).isEqualTo(201);
        assertThat(getAs(m, "/api/blogs/address/sanc-release").status()).isEqualTo(200);
        assertThat(notificationsOf(m.id())).containsExactly("MEMBER_SUSPENDED", "MEMBER_UNSUSPENDED");
    }

    @Test
    void 일년_안_정지_세_번이면_강퇴를_권하되_지난_정지와_다른_블로그_정지는_뺀다() throws Exception {
        Member owner = member("장4");
        long a = createBlog(owner, "sanc-count", "PUBLIC", "FREE");
        long b = createBlog(member("비장4"), "sanc-count-b", "PUBLIC", "FREE");
        Member m = member("세번");
        addMember(a, m.id(), "MEMBER");
        addMember(b, m.id(), "MEMBER");
        jdbc.update("INSERT INTO blog_sanctions (blog_id, user_id, handled_by, sanction_type, suspend_period, starts_at, "
                + "ends_at) VALUES (?, ?, ?, 'SUSPEND', 'DAYS_3', ?, ?)", a, m.id(), owner.id(),
                clock.now().minusDays(400), clock.now().minusDays(397));
        jdbc.update("INSERT INTO blog_sanctions (blog_id, user_id, handled_by, sanction_type, suspend_period, starts_at, "
                + "ends_at) VALUES (?, ?, ?, 'SUSPEND', 'DAYS_3', ?, ?)", b, m.id(), owner.id(),
                clock.now().minusDays(10), clock.now().minusDays(7));
        sanction(owner, a, m.id(), suspend("DAYS_3"));
        sanction(owner, a, m.id(), "{\"type\":\"UNSUSPEND\"}"); // 해제된 정지도 센다
        sanction(owner, a, m.id(), suspend("DAYS_3"));

        Res h = getAs(owner, "/api/blogs/" + a + "/members/" + m.id() + "/sanctions");
        assertThat(h.json().get("suspendCountInWindow").asInt()).isEqualTo(2);
        assertThat(h.json().get("kickSuggested").asBoolean()).isFalse();
        assertThat(h.json().get("items").get(0).get("type").asText()).isEqualTo("SUSPEND");

        sanction(owner, a, m.id(), "{\"type\":\"UNSUSPEND\"}");
        sanction(owner, a, m.id(), suspend("DAYS_3"));
        h = getAs(owner, "/api/blogs/" + a + "/members/" + m.id() + "/sanctions");
        assertThat(h.json().get("suspendCountInWindow").asInt()).isEqualTo(3);
        assertThat(h.json().get("kickSuggested").asBoolean()).isTrue();
        assertThat(role(a, m.id())).isEqualTo("MEMBER"); // 자동 강퇴는 없다
    }

    @Test
    void 강퇴하면_멤버에서_빠지고_글은_탈퇴한_계정이고_블랙리스트에_해시로만_오른다() throws Exception {
        Member owner = member("장5");
        long a = createBlog(owner, "sanc-kick", "PUBLIC", "FREE");
        long target = createUser("kick@test.com", PW, "강퇴대상", "홍길동", "01099998888");
        Member m = new Member(target, "kick@test.com", loginAs("kick@test.com", PW, false), new java.util.ArrayList<>());
        addMember(a, m.id(), "MEMBER");
        jdbc.update("INSERT INTO posts (blog_id, author_id, title, content) VALUES (?, ?, '글', '본문')", a, m.id());

        Res r = sanction(owner, a, m.id(), "{\"type\":\"KICK\",\"reason\":\"광고\"}");
        assertThat(r.status()).isEqualTo(201);
        assertThat(role(a, m.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT author_left FROM posts WHERE author_id = ?", Boolean.class, m.id()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT member_count FROM blogs WHERE id = ?", Integer.class, a)).isEqualTo(1);
        assertThat(notificationsOf(m.id())).containsExactly("MEMBER_KICKED");

        var row = jdbc.queryForMap("SELECT name_hash, email_hash, phone_hash FROM blog_blacklists WHERE blog_id = ?", a);
        String all = row.values().toString();
        assertThat(all).doesNotContain("홍길동", "kick@test.com", "01099998888");
        assertThat((String) row.get("phone_hash")).hasSize(64);

        Res list = getAs(owner, "/api/blogs/" + a + "/blacklist");
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.json().get("items").get(0).get("kickReason").asText()).isEqualTo("광고");
        assertThat(list.json().get("items").get(0).get("kickedByNickname").asText()).isEqualTo("장5");
        assertThat(list.body()).doesNotContain("hash", (String) row.get("email_hash"));

        Res again = postAs(m, "/api/blogs/" + a + "/join-requests", null);
        assertThat(again.status()).isEqualTo(403);
        assertThat(again.code()).isEqualTo("BLOG_BLACKLISTED");
    }

    @Test
    void 탈퇴_후_재가입하거나_같은_전화번호의_다른_이메일이면_그_블로그에만_못_들어간다() throws Exception {
        Member owner = member("장6");
        long a = createBlog(owner, "sanc-rejoin", "PUBLIC", "FREE");
        long other = createBlog(member("남장"), "sanc-other", "PUBLIC", "FREE");
        long target = createUser("rejoin@test.com", PW, "재가입", "김철수", "01077776666");
        addMember(a, target, "MEMBER");
        sanction(owner, a, target, "{\"type\":\"KICK\"}");

        // 회원탈퇴 후 같은 이메일로 재가입
        var login = loginAs("rejoin@test.com", PW, false);
        var res = mvc.perform(com.mainblog.support.AuthTestSupport.withCsrf(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/members/me/withdrawal"))
                .cookie(login.cookies()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + PW + "\"}")).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(204);
        long again = createUser("rejoin@test.com", PW, "재가입2", "김철수", "01011112222");
        Member back = new Member(again, "rejoin@test.com", loginAs("rejoin@test.com", PW, false), new java.util.ArrayList<>());
        assertThat(postAs(back, "/api/blogs/" + a + "/join-requests", null).code()).isEqualTo("BLOG_BLACKLISTED");
        assertThat(postAs(back, "/api/blogs/" + other + "/join-requests", null).status()).isEqualTo(201);

        long samePhone = createUser("phone@test.com", PW, "전화같음", "이영희", "010-7777-6666");
        Member p = new Member(samePhone, "phone@test.com", loginAs("phone@test.com", PW, false), new java.util.ArrayList<>());
        assertThat(postAs(p, "/api/blogs/" + a + "/join-requests", null).code()).isEqualTo("BLOG_BLACKLISTED");
    }

    @Test
    void 부블로그장_제재는_블로그장만_하고_블로그장은_대상이_아니다() throws Exception {
        Member owner = member("장7");
        long a = createBlog(owner, "sanc-manager", "PUBLIC", "FREE");
        Member mgr = member("관리부장");
        Member mgr2 = member("다른부장");
        Member m = member("일반");
        addMember(a, mgr.id(), "MEMBER");
        addMember(a, mgr2.id(), "MEMBER");
        addMember(a, m.id(), "MEMBER");
        setManagerPerms(a, mgr.id(), false, true, false);
        setManagerPerms(a, mgr2.id(), false, true, false);

        assertThat(sanction(mgr, a, m.id(), "{\"type\":\"WARNING\"}").status()).isEqualTo(201);
        assertThat(sanction(mgr, a, mgr2.id(), "{\"type\":\"WARNING\"}").status()).isEqualTo(403);
        assertThat(sanction(mgr, a, owner.id(), "{\"type\":\"WARNING\"}").status()).isEqualTo(403);
        assertThat(sanction(owner, a, mgr2.id(), suspend("DAYS_3")).status()).isEqualTo(201);
        assertThat(sanction(owner, a, member("밖").id(), "{\"type\":\"WARNING\"}").status()).isEqualTo(404);
        assertThat(sanction(m, a, mgr.id(), "{\"type\":\"WARNING\"}").status()).isEqualTo(403);
    }

    @Test
    void 블로그장이_바뀌어도_블랙리스트는_열람만_된다() throws Exception {
        Member owner = member("장8");
        long a = createBlog(owner, "sanc-handover", "PUBLIC", "FREE");
        Member next = member("새장");
        Member mgr = member("열람부장");
        Member viewOnly = member("정보만");
        addMember(a, next.id(), "MEMBER");
        addMember(a, mgr.id(), "MEMBER");
        addMember(a, viewOnly.id(), "MEMBER");
        setManagerPerms(a, mgr.id(), false, true, false);
        setManagerPerms(a, viewOnly.id(), true, false, false);
        Member k = member("강퇴8");
        addMember(a, k.id(), "MEMBER");
        sanction(owner, a, k.id(), "{\"type\":\"KICK\",\"reason\":\"욕설\"}");
        postAs(owner, "/api/blogs/" + a + "/delegation", "{\"toUserId\":" + next.id() + "}");
        postAs(next, "/api/blogs/" + a + "/delegation/acceptance", null);

        assertThat(getAs(next, "/api/blogs/" + a + "/blacklist").json().get("totalCount").asInt()).isEqualTo(1);
        assertThat(getAs(mgr, "/api/blogs/" + a + "/blacklist").status()).isEqualTo(200);
        assertThat(getAs(viewOnly, "/api/blogs/" + a + "/blacklist").status()).isEqualTo(403);
        assertThat(deleteAs(next, "/api/blogs/" + a + "/blacklist").status()).isIn(404, 405);
    }
}
