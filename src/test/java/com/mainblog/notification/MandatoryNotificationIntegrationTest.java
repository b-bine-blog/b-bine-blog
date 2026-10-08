package com.mainblog.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.social.SocialTestBase;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 끌 수 없는 운영 알림 (003 T014, US2-1~12, SC-003, SC-004, D-102 #22~#25).
 * 받는 사람은 끌 수 있는 9가지를 모두 끈 상태다. 대부분 002·004에 있는 동작을 받는 사람과 탭(3.6 표)으로 확인한다.
 */
class MandatoryNotificationIntegrationTest extends SocialTestBase {

    private static final LocalDateTime PRESSED = LocalDateTime.of(2026, 10, 2, 15, 0);

    private void muteAll(long userId) {
        jdbc.update("INSERT INTO notification_settings (user_id, post_comment, comment_reply, post_like, new_follower, "
                + "blog_subscribed, join_request, join_result, report_result, notice) "
                + "VALUES (?, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE)", userId);
    }

    private Member muted(String nickname) throws Exception {
        Member m = member(nickname);
        muteAll(m.id());
        return m;
    }

    private long count(Member m, String typeAndTab) {
        return notificationTabsOf(m.id()).stream().filter(typeAndTab::equals).count();
    }

    private void runAt(LocalDateTime at) {
        clock.setTo(at);
        runDaily();
    }

    @Test
    void 폐쇄_예정은_설정과_관계없이_멤버와_구독자에게_시점마다_한_번씩_철회_뒤에는_더_가지_않는다() throws Exception {
        clock.setTo(PRESSED.minusHours(1));
        Member owner = muted("장");
        long blog = createBlog(owner, "must-close", "PUBLIC", "FREE");
        Member manager = muted("부장");
        addMember(blog, manager.id(), "MANAGER");
        Member member = muted("멤버");
        addMember(blog, member.id(), "MEMBER");
        subscribe(blog, member.id()); // 멤버이면서 구독자도 한 번
        Member subscriber = muted("구독자");
        subscribe(blog, subscriber.id());
        Member outsider = muted("남");
        long other = createBlog(member("남장"), "must-close-other", "PUBLIC", "FREE");
        addMember(other, outsider.id(), "MEMBER");

        clock.setTo(PRESSED);
        assertThat(postAs(relogin(owner), "/api/blogs/" + blog + "/closing", null).status()).isEqualTo(200);
        for (Member m : new Member[] {manager, member, subscriber}) {
            assertThat(notificationTabsOf(m.id())).as(m.email()).containsExactly("BLOG_CLOSING:BLOG");
        }
        assertThat(notificationsOf(owner.id())).isEmpty();
        assertThat(notificationsOf(outsider.id())).isEmpty();

        // 그 사이 들어온 구독자는 3일 전부터 받는다(D-102 #22). 서버가 10/6·10/7에 꺼져 있었으면 10/8에 밀린 몫을 한 번씩(#23)
        Member late = muted("늦은구독");
        subscribe(blog, late.id());
        runAt(LocalDateTime.of(2026, 10, 8, 4, 0));
        runDaily();
        assertThat(count(late, "BLOG_CLOSING:BLOG")).isEqualTo(2);
        assertThat(count(member, "BLOG_CLOSING:BLOG")).isEqualTo(3);

        clock.setTo(LocalDateTime.of(2026, 10, 8, 10, 0));
        assertThat(deleteAs(relogin(owner), "/api/blogs/" + blog + "/closing").status()).isEqualTo(200);
        for (Member m : new Member[] {manager, member, subscriber, late}) {
            assertThat(count(m, "BLOG_CLOSING_WITHDRAWN:BLOG")).as(m.email()).isEqualTo(1);
        }
        int before = notificationCount(member.id());
        runAt(LocalDateTime.of(2026, 10, 9, 4, 0));
        assertThat(notificationCount(member.id())).isEqualTo(before);
        assertThat(notificationsOf(owner.id())).isEmpty();
    }

    @Test
    void 위임_요청과_결과는_블로그_탭이고_폐쇄_버튼으로_취소된_위임은_따로_알리지_않는다() throws Exception {
        Member owner = muted("위임장");
        long blog = createBlog(owner, "must-dele", "PUBLIC", "FREE");
        Member z = muted("받을이");
        addMember(blog, z.id(), "MEMBER");

        postAs(owner, "/api/blogs/" + blog + "/delegation", "{\"toUserId\":" + z.id() + "}");
        assertThat(notificationTabsOf(z.id())).containsExactly("DELEGATION_REQUESTED:BLOG");
        postAs(z, "/api/blogs/" + blog + "/delegation/rejection", null);
        assertThat(notificationTabsOf(owner.id())).containsExactly("DELEGATION_REJECTED:BLOG");

        postAs(owner, "/api/blogs/" + blog + "/delegation", "{\"toUserId\":" + z.id() + "}");
        clock.advance(Duration.ofDays(7).plusMinutes(1));
        runDaily();
        assertThat(notificationTabsOf(owner.id())).containsExactly("DELEGATION_REJECTED:BLOG",
                "DELEGATION_CANCELED:BLOG");

        owner = relogin(owner);
        z = relogin(z);
        postAs(owner, "/api/blogs/" + blog + "/delegation", "{\"toUserId\":" + z.id() + "}");
        postAs(owner, "/api/blogs/" + blog + "/closing", null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_delegations WHERE blog_id = ? AND status = 'PENDING'",
                Integer.class, blog)).isZero();
        assertThat(notificationsOf(z.id())).containsExactly("DELEGATION_REQUESTED", "DELEGATION_REQUESTED",
                "DELEGATION_REQUESTED", "BLOG_CLOSING");
        deleteAs(owner, "/api/blogs/" + blog + "/closing");

        postAs(owner, "/api/blogs/" + blog + "/delegation", "{\"toUserId\":" + z.id() + "}");
        postAs(z, "/api/blogs/" + blog + "/delegation/acceptance", null);
        assertThat(count(owner, "DELEGATION_ACCEPTED:BLOG")).isEqualTo(1);
    }

    @Test
    void 비공개_전환은_구독자_모두에게_가고_바꾼_본인은_빼며_일부_공개_전환은_알리지_않는다() throws Exception {
        Member owner = muted("비공장");
        long blog = createBlog(owner, "must-private", "PUBLIC", "FREE");
        subscribe(blog, owner.id()); // 예전에 구독해 둔 행이 있어도 바꾼 본인은 받지 않는다
        Member s1 = muted("구독1");
        Member s2 = muted("구독2");
        subscribe(blog, s1.id());
        subscribe(blog, s2.id());
        assertThat(patchAs(owner, "/api/blogs/" + blog, "{\"visibility\":\"LINK\"}").status()).isEqualTo(200);
        assertThat(notificationsOf(s1.id())).isEmpty();
        assertThat(patchAs(owner, "/api/blogs/" + blog, "{\"visibility\":\"PRIVATE\"}").status()).isEqualTo(200);
        assertThat(notificationTabsOf(s1.id())).containsExactly("BLOG_PRIVATE:BLOG");
        assertThat(notificationTabsOf(s2.id())).containsExactly("BLOG_PRIVATE:BLOG");
        assertThat(notificationsOf(owner.id())).isEmpty();
        assertThat(subscribed(blog, s1.id())).isTrue();
    }

    @Test
    void 내_글_삭제와_멤버_제재는_운영_탭() throws Exception {
        BlogFixture b = blog("must-sanction", "PUBLIC");
        Member m = memberOf(b.blogId(), "제재대상");
        muteAll(m.id());
        long post = writePost(m, b.blogId(), "글", "내용");
        assertThat(deleteAs(b.owner(), "/api/posts/" + post).status()).isEqualTo(204);
        String url = "/api/blogs/" + b.blogId() + "/members/" + m.id() + "/sanctions";
        postAs(b.owner(), url, "{\"type\":\"WARNING\",\"reason\":\"주의\"}");
        postAs(b.owner(), url, "{\"type\":\"SUSPEND\",\"period\":\"DAYS_3\",\"reason\":\"도배\"}");
        postAs(b.owner(), url, "{\"type\":\"UNSUSPEND\"}");
        postAs(b.owner(), url, "{\"type\":\"KICK\"}");
        assertThat(notificationTabsOf(m.id())).containsExactly("POST_DELETED:OPERATION", "MEMBER_WARNED:OPERATION",
                "MEMBER_SUSPENDED:OPERATION", "MEMBER_UNSUSPENDED:OPERATION", "MEMBER_KICKED:OPERATION");
    }

    @Test
    void 블랙리스트_해제_문의_결과는_블로그_탭() throws Exception {
        Member owner = member("문의장");
        long blog = createBlog(owner, "must-appeal", "PUBLIC", "FREE");
        long kicked = createUser("kicked-appeal@test.com", PW, "강퇴됨", "홍길동", "01055554444");
        addMember(blog, kicked, "MEMBER");
        postAs(owner, "/api/blogs/" + blog + "/members/" + kicked + "/sanctions", "{\"type\":\"KICK\"}");
        long n1 = createUser("n1-appeal@test.com", PW, "문의1", "김영수", "010-5555-4444");
        long n2 = createUser("n2-appeal@test.com", PW, "문의2", "이영희", "010-5555-4444");
        muteAll(n1);
        muteAll(n2);
        Member a1 = new Member(n1, "n1-appeal@test.com", loginAs("n1-appeal@test.com", PW, false), new java.util.ArrayList<>());
        Member a2 = new Member(n2, "n2-appeal@test.com", loginAs("n2-appeal@test.com", PW, false), new java.util.ArrayList<>());
        long id1 = postAs(a1, "/api/blogs/" + blog + "/blacklist-appeals", "{\"content\":\"아닙니다\"}").json()
                .get("id").asLong();
        long id2 = postAs(a2, "/api/blogs/" + blog + "/blacklist-appeals", "{\"content\":\"아닙니다\"}").json()
                .get("id").asLong();
        postAs(owner, "/api/blogs/" + blog + "/blacklist-appeals/" + id1 + "/release", null);
        postAs(owner, "/api/blogs/" + blog + "/blacklist-appeals/" + id2 + "/rejection", null);
        assertThat(notificationTabsOf(n1)).containsExactly("APPEAL_RELEASED:BLOG");
        assertThat(notificationTabsOf(n2)).containsExactly("APPEAL_REJECTED:BLOG");
    }

    @Test
    void 블로그_탭에서_위임과_해제_문의_결과를_함께_본다() throws Exception {
        Member m = member("탭확인");
        insertNotification(m.id(), "DELEGATION_REQUESTED", "BLOG", clock.now().minusMinutes(1));
        insertNotification(m.id(), "MEMBER_WARNED", "OPERATION", clock.now());
        assertThat(getAs(m, "/api/notifications?tab=BLOG").json().get("items")).hasSize(1);
        assertThat(getAs(m, "/api/notifications?tab=OPERATION").json().get("items").get(0).get("type").asText())
                .isEqualTo("MEMBER_WARNED");
    }
}
