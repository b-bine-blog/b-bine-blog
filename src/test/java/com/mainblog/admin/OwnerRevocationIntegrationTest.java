package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 블로그장 바로 강퇴(권한 박탈) (005 T027, US3-3~9, FR-022~FR-029, D-17, D-54, D-103 #14~#17). */
class OwnerRevocationIntegrationTest extends AdminTestBase {

    private static final LocalDateTime PRESSED = LocalDateTime.of(2026, 10, 2, 15, 0);

    private Res kick(Member admin, long blogId, long ownerId) throws Exception {
        return postAs(admin, "/api/admin/blogs/" + blogId + "/owner-revocation", "{\"ownerId\":" + ownerId + "}");
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
    void 바로_강퇴하면_가장_먼저_된_부블로그장이_수락_없이_블로그장이_된다() throws Exception {
        clock.setTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        Member admin = admin("관리자1");
        BlogFixture b = blog("kick-pass", "PUBLIC");
        Member s1 = member("부장1");
        addMember(b.blogId(), s1.id(), "MANAGER");
        clock.setTo(LocalDateTime.of(2026, 10, 3, 10, 0));
        Member s2 = member("부장2");
        addMember(b.blogId(), s2.id(), "MANAGER");
        admin = relogin(admin);

        Res r = kick(admin, b.blogId(), b.owner().id());
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().get("cause").asText()).isEqualTo("DIRECT_KICK");
        assertThat(r.json().get("newOwner").get("id").asLong()).isEqualTo(s1.id());
        assertThat(r.json().get("closeScheduledAt").isNull()).isTrue();
        assertThat(role(b.blogId(), s1.id())).isEqualTo("OWNER");
        assertThat(role(b.blogId(), s2.id())).isEqualTo("MANAGER");
        assertThat(role(b.blogId(), b.owner().id())).isEqualTo("MEMBER");
        assertThat(status(b.blogId())).isEqualTo("ACTIVE");
        assertThat(notificationsOf(b.owner().id())).containsExactly("OWNER_REVOKED");
        assertThat(jdbc.queryForObject("SELECT tab FROM notifications WHERE notification_type = 'OWNER_REVOKED'",
                String.class)).isEqualTo("OPERATION");
        assertThat(notificationsOf(s1.id())).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM owner_revocations WHERE blog_id = ? AND user_id = ? "
                + "AND new_owner_id = ? AND cause = 'DIRECT_KICK'", b.blogId(), b.owner().id(), s1.id())).isEqualTo(1);
        assertThat(actionTypes()).containsExactly("OWNER_KICK", "AUTO_DELEGATE");
        assertThat(((Number) actions("OWNER_KICK").get(0).get("target_id")).longValue()).isEqualTo(b.owner().id());

        // 새 블로그장은 전 블로그장을 002 블로그 단위 제재로 정지할 수 있다(FR-026)
        Res sanction = postAs(relogin(s1), "/api/blogs/" + b.blogId() + "/members/" + b.owner().id() + "/sanctions",
                "{\"type\":\"SUSPEND\",\"period\":\"DAYS_3\",\"reason\":\"운영 방해\"}");
        assertThat(sanction.status()).isEqualTo(201);
    }

    @Test
    void 받을_부블로그장이_공개_블로그를_3개_운영해도_넘어간다() throws Exception {
        Member admin = admin("관리자2");
        BlogFixture b = blog("kick-limit", "PUBLIC");
        Member s1 = member("부장많음");
        for (int i = 1; i <= 3; i++) {
            createBlog(s1, "kick-own" + i, "PUBLIC", "FREE");
        }
        addMember(b.blogId(), s1.id(), "MANAGER");
        assertThat(kick(admin, b.blogId(), b.owner().id()).status()).isEqualTo(201);
        assertThat(role(b.blogId(), s1.id())).isEqualTo("OWNER");
        assertThat(postAs(s1, "/api/blogs", blogJson("하나 더", "kick-own4", "PUBLIC", "FREE")).code())
                .isEqualTo("BLOG_LIMIT_EXCEEDED");
    }

    @Test
    void 부블로그장이_없으면_7일_뒤_04시_폐쇄_예약이고_박탈된_사람은_알림에서_빠지며_아무도_철회할_수_없다() throws Exception {
        clock.setTo(PRESSED.minusHours(1));
        Member admin = admin("관리자3");
        BlogFixture b = blog("kick-close", "PUBLIC");
        Member m = memberOf(b.blogId(), "폐쇄멤버3");
        Member u = member("폐쇄구독3");
        subscribe(b.blogId(), u.id());
        subscribe(b.blogId(), m.id());
        subscribe(b.blogId(), b.owner().id());

        clock.setTo(PRESSED);
        Res r = kick(relogin(admin), b.blogId(), b.owner().id());
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().get("closeScheduledAt").asText()).startsWith("2026-10-09T04:00");
        assertThat(r.json().get("newOwner").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT close_reason FROM blogs WHERE id = ?", String.class, b.blogId()))
                .isEqualTo("OWNER_REVOKED");
        assertThat(notificationContents(m.id(), "BLOG_CLOSING").get(0)).contains("블로그장 권한 박탈로 인한 폐쇄 조치");
        assertThat(closingCount(m)).isEqualTo(1);
        assertThat(closingCount(u)).isEqualTo(1);
        assertThat(notificationsOf(b.owner().id())).containsExactly("OWNER_REVOKED");
        assertThat(actionTypes()).containsExactly("OWNER_KICK", "CLOSE_SCHEDULE");

        runAt(LocalDateTime.of(2026, 10, 6, 4, 0));
        runAt(LocalDateTime.of(2026, 10, 8, 4, 0));
        runDaily();
        assertThat(closingCount(m)).isEqualTo(3);
        assertThat(closingCount(u)).isEqualTo(3);
        assertThat(closingCount(b.owner())).isZero();
        assertThat(count("SELECT COUNT(*) FROM blog_closing_notices WHERE blog_id = ? AND user_id = ?", b.blogId(),
                b.owner().id())).isZero();

        // 폐쇄 전까지 글쓰기·댓글이 된다(FR-025), 아무도 철회할 수 없다(FR-029)
        Member member = relogin(m);
        long post = postAs(member, "/api/blogs/" + b.blogId() + "/posts", postJson("폐쇄 전 글", "본문", null, List.of()))
                .json().get("id").asLong();
        assertThat(postAs(member, "/api/posts/" + post + "/comments", json(Map.of("content", "댓글"))).status())
                .isEqualTo(201);
        assertThat(deleteAs(relogin(b.owner()), "/api/blogs/" + b.blogId() + "/closing").status()).isEqualTo(403);
        assertThat(deleteAs(member, "/api/blogs/" + b.blogId() + "/closing").status()).isEqualTo(403);

        runAt(LocalDateTime.of(2026, 10, 9, 4, 0));
        assertThat(status(b.blogId())).isEqualTo("CLOSED");
        assertThat(closingCount(b.owner())).isZero();
    }

    @Test
    void 블로그장이_폐쇄를_누른_블로그에서_부블로그장이_있으면_위임되고_새_블로그장이_철회할_수_있다() throws Exception {
        clock.setTo(PRESSED.minusHours(1));
        Member admin = admin("관리자4");
        BlogFixture b = blog("kick-closing-mgr", "PUBLIC");
        Member s = member("부장4");
        addMember(b.blogId(), s.id(), "MANAGER");
        clock.setTo(PRESSED);
        String scheduled = postAs(relogin(b.owner()), "/api/blogs/" + b.blogId() + "/closing", null).json()
                .get("closeScheduledAt").asText();

        Res r = kick(relogin(admin), b.blogId(), b.owner().id());
        assertThat(r.json().get("newOwner").get("id").asLong()).isEqualTo(s.id());
        assertThat(status(b.blogId())).isEqualTo("CLOSING");
        assertThat(jdbc.queryForObject("SELECT close_scheduled_at FROM blogs WHERE id = ?", LocalDateTime.class,
                b.blogId()).toString()).startsWith(scheduled.substring(0, 16));
        Res withdraw = deleteAs(relogin(s), "/api/blogs/" + b.blogId() + "/closing");
        assertThat(withdraw.status()).isIn(200, 204);
        assertThat(status(b.blogId())).isEqualTo("ACTIVE");
    }

    @Test
    void 블로그장이_폐쇄를_누른_블로그에서_부블로그장이_없으면_시각을_유지하고_다시_알리지_않는다() throws Exception {
        clock.setTo(PRESSED.minusHours(1));
        Member admin = admin("관리자5");
        BlogFixture b = blog("kick-closing-alone", "PUBLIC");
        Member m = memberOf(b.blogId(), "멤버5");
        clock.setTo(PRESSED);
        String scheduled = postAs(relogin(b.owner()), "/api/blogs/" + b.blogId() + "/closing", null).json()
                .get("closeScheduledAt").asText();
        assertThat(closingCount(m)).isEqualTo(1);

        clock.setTo(PRESSED.plusDays(1));
        Res r = kick(relogin(admin), b.blogId(), b.owner().id());
        assertThat(r.json().get("closeScheduledAt").asText()).isEqualTo(scheduled);
        assertThat(closingCount(m)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT close_reason FROM blogs WHERE id = ?", String.class, b.blogId()))
                .isEqualTo("OWNER");
        runAt(LocalDateTime.of(2026, 10, 6, 4, 0));
        assertThat(closingCount(m)).isEqualTo(2);
        assertThat(closingCount(b.owner())).isZero();
        assertThat(actionTypes()).containsExactly("OWNER_KICK", "CLOSE_SCHEDULE");
    }

    @Test
    void 대기_중_위임_요청은_자동_취소되고_받는_멤버에게_알린다() throws Exception {
        Member admin = admin("관리자6");
        BlogFixture b = blog("kick-delegation", "PUBLIC");
        Member x = memberOf(b.blogId(), "받을멤버6");
        assertThat(postAs(b.owner(), "/api/blogs/" + b.blogId() + "/delegation", "{\"toUserId\":" + x.id() + "}")
                .status()).isIn(200, 201);
        int before = notificationsOf(x.id()).size();

        Res r = kick(admin, b.blogId(), b.owner().id());
        assertThat(r.json().get("canceledDelegations").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM blog_delegations WHERE blog_id = ?", String.class,
                b.blogId())).isEqualTo("AUTO_CANCELED");
        List<String> got = notificationsOf(x.id()).subList(before, notificationsOf(x.id()).size());
        assertThat(got.stream().filter("DELEGATION_CANCELED"::equals).count()).isEqualTo(1);
        assertThat(notificationsOf(b.owner().id())).doesNotContain("DELEGATION_CANCELED");
    }

    @Test
    void 지금_블로그장이_아니면_409이고_관리자용_멤버_제재_API는_없다() throws Exception {
        Member admin = admin("관리자7");
        BlogFixture b = blog("kick-deny", "PUBLIC");
        Member m = memberOf(b.blogId(), "멤버7");
        Res wrong = kick(admin, b.blogId(), m.id());
        assertThat(wrong.status()).isEqualTo(409);
        assertThat(wrong.code()).isEqualTo("OWNER_CHANGED");
        assertThat(postAs(admin, "/api/admin/blogs/" + b.blogId() + "/owner-revocation",
                "{\"ownerId\":" + b.owner().id() + ",\"reason\":\"" + "가".repeat(501) + "\"}").status()).isEqualTo(400);
        assertThat(postAs(admin, "/api/admin/blogs/" + b.blogId() + "/owner-revocation", "{}").status())
                .isEqualTo(400);

        // FR-010: 메인 관리자는 일반 멤버를 경고·정지·강제 퇴장하지 않는다
        assertThat(postAs(admin, "/api/admin/blogs/" + b.blogId() + "/members/" + m.id() + "/sanctions",
                "{\"type\":\"SUSPEND\",\"period\":\"DAYS_3\",\"reason\":\"x\"}").status()).isIn(404, 405);
        assertThat(postAs(admin, "/api/admin/members/" + m.id() + "/suspension", null).status()).isIn(404, 405);
        assertThat(postAs(admin, "/api/blogs/" + b.blogId() + "/members/" + m.id() + "/sanctions",
                "{\"type\":\"SUSPEND\",\"period\":\"DAYS_3\",\"reason\":\"x\"}").status()).isEqualTo(403);
        assertThat(role(b.blogId(), m.id())).isEqualTo("MEMBER");
        assertThat(count("SELECT COUNT(*) FROM owner_revocations")).isZero();
        assertThat(actionTypes()).isEmpty();
        JsonNode detail = getAs(admin, "/api/admin/blogs/" + b.blogId()).json();
        assertThat(detail.get("owner").get("id").asLong()).isEqualTo(b.owner().id());
    }
}
