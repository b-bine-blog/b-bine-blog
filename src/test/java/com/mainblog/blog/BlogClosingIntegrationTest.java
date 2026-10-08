package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** US5 블로그 폐쇄와 철회 (T046, BLG-09, FR-072~FR-080, SC-006). 시각은 모두 한국 시간. */
class BlogClosingIntegrationTest extends BlogTestBase {

    private static final LocalDateTime PRESSED = LocalDateTime.of(2026, 10, 2, 15, 0);

    /** 블로그장, 부블로그장, 멤버, 구독자(비멤버), 다른 블로그 멤버를 만든다. */
    private final class Fixture {
        Member owner;
        Member manager;
        Member member;
        Member subscriber;
        Member outsider;
        long blog;

        Fixture(String address) throws Exception {
            clock.setTo(PRESSED.minusHours(1));
            owner = member("장");
            blog = createBlog(owner, address, "PUBLIC", "APPROVAL");
            manager = member("부장");
            addMember(blog, manager.id(), "MANAGER");
            member = member("멤버");
            addMember(blog, member.id(), "MEMBER");
            subscribe(blog, member.id()); // 멤버이면서 구독자여도 한 번만
            subscriber = member("구독자");
            subscribe(blog, subscriber.id());
            outsider = member("남");
            long other = createBlog(member("남장"), address + "-other", "PUBLIC", "FREE");
            addMember(other, outsider.id(), "MEMBER");
            subscribe(blog, owner.id()); // 블로그장은 구독해도 받지 않는다
        }

        List<Member> recipients() {
            return List.of(manager, member, subscriber);
        }

        Res close() throws Exception {
            owner = relogin(owner);
            return postAs(owner, "/api/blogs/" + blog + "/closing", null);
        }
    }

    private long closingCount(Member m) {
        return notificationsOf(m.id()).stream().filter("BLOG_CLOSING"::equals).count();
    }

    private void runAt(LocalDateTime at) {
        clock.setTo(at);
        runDaily();
    }

    private String status(long blog) {
        return jdbc.queryForObject("SELECT status FROM blogs WHERE id = ?", String.class, blog);
    }

    @Test
    void 누른_날짜_7일_뒤_04시에_폐쇄되고_즉시_3일_전_1일_전에_한_번씩_알린다() throws Exception {
        Fixture f = new Fixture("close-full");
        clock.setTo(PRESSED);
        Res r = f.close();
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("status").asText()).isEqualTo("CLOSING");
        assertThat(r.json().get("closeScheduledAt").asText()).startsWith("2026-10-09T04:00");
        for (Member m : f.recipients()) {
            assertThat(closingCount(m)).as(m.email()).isEqualTo(1);
        }
        assertThat(notificationsOf(f.owner.id())).isEmpty();
        assertThat(notificationsOf(f.outsider.id())).isEmpty();

        runAt(LocalDateTime.of(2026, 10, 5, 4, 0));
        assertThat(closingCount(f.member)).isEqualTo(1);
        runAt(LocalDateTime.of(2026, 10, 6, 4, 0));
        runDaily(); // 같은 시각에 두 번 돌아도 한 번만
        f.recipients().forEach(m -> assertThat(closingCount(m)).isEqualTo(2));
        runAt(LocalDateTime.of(2026, 10, 8, 4, 0));
        assertThat(status(f.blog)).isEqualTo("CLOSING");
        runAt(LocalDateTime.of(2026, 10, 9, 4, 0));
        runDaily();

        f.recipients().forEach(m -> assertThat(closingCount(m)).isEqualTo(3));
        assertThat(notificationsOf(f.owner.id())).isEmpty();
        assertThat(notificationsOf(f.outsider.id())).isEmpty();
        assertThat(status(f.blog)).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_closing_notices WHERE blog_id = ?", Integer.class,
                f.blog)).isEqualTo(9);
        assertThat(getAs(null, "/api/blogs/address/close-full").status()).isEqualTo(404);
    }

    @Test
    void 폐쇄_예정_중에도_평소처럼_운영된다() throws Exception {
        Fixture f = new Fixture("close-running");
        clock.setTo(PRESSED);
        f.close();
        Member m = relogin(f.member);
        assertThat(getAs(m, "/api/blogs/address/close-running").status()).isEqualTo(200);
        Member applicant = member("신청");
        assertThat(postAs(applicant, "/api/blogs/" + f.blog + "/join-requests", null).status()).isEqualTo(201);
        assertThat(postAs(f.owner, "/api/blogs/" + f.blog + "/closing", null).code())
                .isEqualTo("BLOG_ALREADY_CLOSING");
    }

    @Test
    void 철회하면_철회_알림_한_번이고_이후_알림과_폐쇄가_없다() throws Exception {
        Fixture f = new Fixture("close-withdraw");
        clock.setTo(PRESSED);
        f.close();
        clock.setTo(LocalDateTime.of(2026, 10, 5, 10, 0));
        Member owner = relogin(f.owner);
        Res r = deleteAs(owner, "/api/blogs/" + f.blog + "/closing");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("status").asText()).isEqualTo("ACTIVE");
        assertThat(r.json().get("closeScheduledAt").isNull()).isTrue();

        runAt(LocalDateTime.of(2026, 10, 6, 4, 0));
        runAt(LocalDateTime.of(2026, 10, 8, 4, 0));
        runAt(LocalDateTime.of(2026, 10, 9, 4, 0));
        for (Member m : f.recipients()) {
            assertThat(notificationsOf(m.id())).containsExactly("BLOG_CLOSING", "BLOG_CLOSING_WITHDRAWN");
        }
        assertThat(status(f.blog)).isEqualTo("ACTIVE");
        assertThat(deleteAs(relogin(owner), "/api/blogs/" + f.blog + "/closing").code())
                .isEqualTo("BLOG_NOT_CLOSING");
    }

    @Test
    void 철회_뒤_다시_폐쇄하면_새_회차로_알린다() throws Exception {
        Fixture f = new Fixture("close-again");
        clock.setTo(PRESSED);
        f.close();
        deleteAs(f.owner, "/api/blogs/" + f.blog + "/closing");
        clock.setTo(PRESSED.plusDays(1));
        assertThat(f.close().json().get("closeScheduledAt").asText()).startsWith("2026-10-10T04:00");
        assertThat(closingCount(f.subscriber)).isEqualTo(2);
    }

    @Test
    void 실행을_건너뛴_날이_있으면_다음_실행에서_밀린_것까지_처리한다() throws Exception {
        Fixture f = new Fixture("close-late");
        clock.setTo(PRESSED);
        f.close();
        runAt(LocalDateTime.of(2026, 10, 10, 4, 0));
        assertThat(status(f.blog)).isEqualTo("CLOSED");
        f.recipients().forEach(m -> assertThat(closingCount(m)).isEqualTo(3));
        runAt(LocalDateTime.of(2026, 10, 11, 4, 0));
        f.recipients().forEach(m -> assertThat(closingCount(m)).isEqualTo(3));
    }

    @Test
    void 자정_경계는_누른_날짜로_정한다() throws Exception {
        Member a = member("경계1");
        long b1 = createBlog(a, "edge-before", "PUBLIC", "FREE");
        Member b = member("경계2");
        long b2 = createBlog(b, "edge-after", "PUBLIC", "FREE");
        clock.setTo(LocalDateTime.of(2026, 10, 2, 23, 59));
        assertThat(postAs(relogin(a), "/api/blogs/" + b1 + "/closing", null).json().get("closeScheduledAt").asText())
                .startsWith("2026-10-09T04:00");
        clock.setTo(LocalDateTime.of(2026, 10, 3, 0, 1));
        assertThat(postAs(relogin(b), "/api/blogs/" + b2 + "/closing", null).json().get("closeScheduledAt").asText())
                .startsWith("2026-10-10T04:00");
    }

    @Test
    void 블로그장만_폐쇄하고_철회한다() throws Exception {
        Fixture f = new Fixture("close-perm");
        clock.setTo(PRESSED);
        setManagerPerms(f.blog, f.manager.id(), true, true, true);
        Member manager = relogin(f.manager);
        assertThat(postAs(manager, "/api/blogs/" + f.blog + "/closing", null).status()).isEqualTo(403);
        f.close();
        assertThat(deleteAs(manager, "/api/blogs/" + f.blog + "/closing").status()).isEqualTo(403);
        assertThat(postAs(relogin(f.member), "/api/blogs/" + f.blog + "/closing", null).status()).isEqualTo(403);
    }

    @Test
    void 폐쇄_버튼은_대기_중_위임을_취소하고_폐쇄는_대기_중_신청과_문의를_거절한다() throws Exception {
        Fixture f = new Fixture("close-pending");
        clock.setTo(PRESSED);
        jdbc.update("INSERT INTO blog_delegations (to_user_id, blog_id, from_user_id, requested_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?)", f.member.id(), f.blog, f.owner.id(), PRESSED, PRESSED.plusDays(7));
        Member applicant = relogin(member("신청자"));
        long req = postAs(applicant, "/api/blogs/" + f.blog + "/join-requests", null).json().get("id").asLong();
        Member kicked = member("강퇴");
        long appeal = insertAppeal(insertBlacklist(f.blog, kicked.id(), f.owner.id()), kicked.id());

        f.close();
        assertThat(jdbc.queryForObject("SELECT status FROM blog_delegations WHERE blog_id = ?", String.class, f.blog))
                .isEqualTo("AUTO_CANCELED");
        runAt(LocalDateTime.of(2026, 10, 9, 4, 0));

        assertThat(jdbc.queryForObject("SELECT status FROM blog_join_requests WHERE id = ?", String.class, req))
                .isEqualTo("AUTO_REJECTED");
        assertThat(jdbc.queryForObject("SELECT status FROM blacklist_appeals WHERE id = ?", String.class, appeal))
                .isEqualTo("REJECTED");
        assertThat(notificationsOf(applicant.id())).contains("BLOG_CLOSED_REQUEST_REJECTED");
        assertThat(notificationsOf(kicked.id())).containsExactly("APPEAL_REJECTED");
        assertThat(Collections.frequency(notificationsOf(applicant.id()), "BLOG_CLOSING")).isZero();
    }
}
