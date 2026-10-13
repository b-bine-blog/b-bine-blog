package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** US9 블랙리스트 해제 문의 (T069, BLG-12, FR-061~FR-063). */
class BlacklistAppealIntegrationTest extends BlogTestBase {

    private Member owner;
    private long blog;
    private Member n;

    /** 홍길동(01055554444)을 강퇴하고, 전화번호만 같은 다른 회원 N을 만든다. */
    private void setUpKick(String address) throws Exception {
        owner = member("장");
        blog = createBlog(owner, address, "PUBLIC", "FREE");
        long kicked = createUser("kicked-" + address + "@test.com", PW, "강퇴" + address.length(), "홍길동", "01055554444");
        addMember(blog, kicked, "MEMBER");
        postAs(owner, "/api/blogs/" + blog + "/members/" + kicked + "/sanctions", "{\"type\":\"KICK\"}");
        String email = "n-" + address + "@test.com";
        long id = createUser(email, PW, "엔" + address.length(), "김영수", "010-5555-4444");
        n = new Member(id, email, loginAs(email, PW, false), new ArrayList<>());
    }

    private Res appeal(Member who) throws Exception {
        return postAs(who, "/api/blogs/" + blog + "/blacklist-appeals", "{\"content\":\"번호를 새로 받았어요\"}");
    }

    @Test
    void 문의하면_일치_여부만_보이고_해제하면_참여_신청할_수_있다() throws Exception {
        setUpKick("appeal-release");
        Res join = postAs(n, "/api/blogs/" + blog + "/join-requests", null);
        assertThat(join.code()).isEqualTo("BLOG_BLACKLISTED");
        assertThat(join.json().get("appealAllowed").asBoolean()).isTrue();

        Res r = appeal(n);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().get("status").asText()).isEqualTo("PENDING");
        assertThat(appeal(n).code()).isEqualTo("APPEAL_PENDING_EXISTS");

        Res list = getAs(owner, "/api/blogs/" + blog + "/blacklist-appeals");
        var item = list.json().get("items").get(0);
        assertThat(item.get("nameMatched").asBoolean()).isFalse();
        assertThat(item.get("phoneMatched").asBoolean()).isTrue();
        assertThat(item.get("content").asText()).isEqualTo("번호를 새로 받았어요");
        String hash = jdbc.queryForObject("SELECT phone_hash FROM blog_blacklists WHERE blog_id = ?", String.class, blog);
        assertThat(list.body()).doesNotContain(hash, "홍길동", "01055554444", "김영수", "Hash");

        long id = item.get("id").asLong();
        Res released = postAs(owner, "/api/blogs/" + blog + "/blacklist-appeals/" + id + "/release", null);
        assertThat(released.status()).isEqualTo(200);
        assertThat(released.json().get("status").asText()).isEqualTo("RELEASED");
        assertThat(notificationsOf(n.id())).containsExactly("APPEAL_RELEASED");
        assertThat(postAs(owner, "/api/blogs/" + blog + "/blacklist-appeals/" + id + "/rejection", null).code())
                .isEqualTo("APPEAL_NOT_PENDING");
        assertThat(postAs(n, "/api/blogs/" + blog + "/join-requests", null).status()).isEqualTo(201);
        assertThat(getAs(owner, "/api/blogs/" + blog + "/blacklist").json().get("items").get(0).get("released")
                .asBoolean()).isTrue();
    }

    @Test
    void 거절하면_블랙리스트는_그대로이고_7일_뒤에_다시_문의한다() throws Exception {
        setUpKick("appeal-reject");
        long id = appeal(n).json().get("id").asLong();
        assertThat(postAs(owner, "/api/blogs/" + blog + "/blacklist-appeals/" + id + "/rejection", null).status())
                .isEqualTo(200);
        assertThat(notificationsOf(n.id())).containsExactly("APPEAL_REJECTED");
        assertThat(postAs(n, "/api/blogs/" + blog + "/join-requests", null).code()).isEqualTo("BLOG_BLACKLISTED");

        clock.advance(Duration.ofDays(6));
        n = relogin(n);
        Res tooSoon = appeal(n);
        assertThat(tooSoon.code()).isEqualTo("APPEAL_RETRY_TOO_SOON");
        assertThat(tooSoon.json().has("retryAt")).isTrue();
        clock.advance(Duration.ofDays(1).plusMinutes(1));
        n = relogin(n);
        assertThat(appeal(n).status()).isEqualTo(201);
    }

    @Test
    void 블랙리스트가_아니면_문의할_수_없고_멤버_관리_권한자만_처리한다() throws Exception {
        setUpKick("appeal-guard");
        Member stranger = member("남");
        assertThat(appeal(stranger).code()).isEqualTo("NOT_BLACKLISTED");
        assertThat(postAs(null, "/api/blogs/" + blog + "/blacklist-appeals", "{}").status()).isEqualTo(401);

        long id = appeal(n).json().get("id").asLong();
        Member m = member("멤버");
        addMember(blog, m.id(), "MEMBER");
        assertThat(getAs(m, "/api/blogs/" + blog + "/blacklist-appeals").status()).isEqualTo(403);
        assertThat(postAs(m, "/api/blogs/" + blog + "/blacklist-appeals/" + id + "/release", null).status())
                .isEqualTo(403);
        Member mgr = member("관리");
        addMember(blog, mgr.id(), "MEMBER");
        setManagerPerms(blog, mgr.id(), false, true, false);
        assertThat(postAs(mgr, "/api/blogs/" + blog + "/blacklist-appeals/" + id + "/release", null).status())
                .isEqualTo(200);
    }
}
