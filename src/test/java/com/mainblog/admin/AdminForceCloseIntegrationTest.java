package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 강제 폐쇄와 그 철회 (005 T020, US2-3·6, FR-013, D-94, D-103 #8, D-104: 관리자 폐쇄는 관리자만 철회). */
class AdminForceCloseIntegrationTest extends AdminTestBase {

    private static final LocalDateTime PRESSED = LocalDateTime.of(2026, 10, 2, 15, 0);

    private long closingCount(Member m) {
        return notificationsOf(m.id()).stream().filter("BLOG_CLOSING"::equals).count();
    }

    private String status(long blog) {
        return jdbc.queryForObject("SELECT status FROM blogs WHERE id = ?", String.class, blog);
    }

    private String closeReason(long blog) {
        return jdbc.queryForObject("SELECT close_reason FROM blogs WHERE id = ?", String.class, blog);
    }

    private void runAt(LocalDateTime at) {
        clock.setTo(at);
        runDaily();
    }

    @Test
    void 강제_폐쇄는_7일_뒤_04시_예약과_같은_알림을_따르고_관리자만_철회할_수_있다() throws Exception {
        clock.setTo(PRESSED.minusHours(1));
        Member admin = admin("관리자1");
        BlogFixture b = blog("force-me", "PUBLIC");
        Member member = memberOf(b.blogId(), "폐쇄멤버");
        Member subscriber = member("폐쇄구독");
        subscribe(b.blogId(), subscriber.id());

        clock.setTo(PRESSED);
        admin = relogin(admin);
        Res r = postAs(admin, "/api/admin/blogs/" + b.blogId() + "/force-close", null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("closeScheduledAt").asText()).startsWith("2026-10-09T04:00");
        assertThat(r.json().get("keptExisting").asBoolean()).isFalse();
        assertThat(closeReason(b.blogId())).isEqualTo("ADMIN");
        assertThat(closingCount(member)).isEqualTo(1);
        assertThat(closingCount(subscriber)).isEqualTo(1);
        assertThat(closingCount(b.owner())).isZero();
        assertThat(actions("BLOG_FORCE_CLOSE")).hasSize(1);

        runAt(LocalDateTime.of(2026, 10, 6, 4, 0));
        runAt(LocalDateTime.of(2026, 10, 8, 4, 0));
        assertThat(closingCount(member)).isEqualTo(3);
        assertThat(closingCount(subscriber)).isEqualTo(3);
        assertThat(closingCount(b.owner())).isZero();

        // 관리자는 블로그장의 철회 버튼을 쓸 수 없고, 블로그장도 관리자 폐쇄는 철회할 수 없다(D-104)
        assertThat(deleteAs(relogin(admin), "/api/blogs/" + b.blogId() + "/closing").status()).isEqualTo(403);
        Res locked = deleteAs(relogin(b.owner()), "/api/blogs/" + b.blogId() + "/closing");
        assertThat(locked.status()).isEqualTo(403);
        assertThat(locked.code()).isEqualTo("ADMIN_CLOSE_LOCKED");
        assertThat(status(b.blogId())).isEqualTo("CLOSING");
        assertThat(closeReason(b.blogId())).isEqualTo("ADMIN");
        assertThat(notificationsOf(member.id())).doesNotContain("BLOG_CLOSING_WITHDRAWN");

        // 관리자가 철회하면 운영 중으로 돌아가고 철회 알림이 가며 RESTORE·BLOG 기록이 남는다
        Res withdraw = deleteAs(relogin(admin), "/api/admin/blogs/" + b.blogId() + "/force-close");
        assertThat(withdraw.status()).isEqualTo(204);
        assertThat(status(b.blogId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs WHERE id = ? AND close_scheduled_at IS NULL "
                + "AND close_reason IS NULL", Integer.class, b.blogId())).isEqualTo(1);
        assertThat(notificationsOf(member.id())).contains("BLOG_CLOSING_WITHDRAWN");
        assertThat(notificationsOf(subscriber.id())).contains("BLOG_CLOSING_WITHDRAWN");
        assertThat(notificationsOf(b.owner().id())).doesNotContain("BLOG_CLOSING_WITHDRAWN");
        Map<String, Object> restore = actions("RESTORE").get(0);
        assertThat(actions("RESTORE")).hasSize(1);
        assertThat(restore.get("target_type")).isEqualTo("BLOG");
        assertThat(((Number) restore.get("target_id")).longValue()).isEqualTo(b.blogId());
        assertThat(((Number) restore.get("admin_id")).longValue()).isEqualTo(admin.id());
        assertThat(restore.get("memo")).isEqualTo("강제 폐쇄 철회");

        // 다시 철회하면 폐쇄 예정이 아니다
        Res again = deleteAs(relogin(admin), "/api/admin/blogs/" + b.blogId() + "/force-close");
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("BLOG_NOT_CLOSING");
        runAt(LocalDateTime.of(2026, 10, 9, 4, 0));
        assertThat(status(b.blogId())).isEqualTo("ACTIVE");
        assertThat(actions("RESTORE")).hasSize(1);
    }

    @Test
    void 블로그장_폐쇄는_블로그장이_철회하고_관리자는_철회할_수_없다() throws Exception {
        clock.setTo(PRESSED);
        Member admin = admin("관리자4");
        BlogFixture b = blog("force-owner", "PUBLIC");
        Member member = memberOf(b.blogId(), "주인폐쇄멤버");
        assertThat(postAs(relogin(b.owner()), "/api/blogs/" + b.blogId() + "/closing", null).status()).isEqualTo(200);

        Res notAdmin = deleteAs(relogin(admin), "/api/admin/blogs/" + b.blogId() + "/force-close");
        assertThat(notAdmin.status()).isEqualTo(409);
        assertThat(notAdmin.code()).isEqualTo("NOT_ADMIN_CLOSE");
        assertThat(status(b.blogId())).isEqualTo("CLOSING");
        assertThat(actionTypes()).isEmpty();

        Res own = deleteAs(relogin(b.owner()), "/api/blogs/" + b.blogId() + "/closing");
        assertThat(own.status()).isEqualTo(200);
        assertThat(status(b.blogId())).isEqualTo("ACTIVE");
        assertThat(notificationsOf(member.id())).contains("BLOG_CLOSING_WITHDRAWN");
    }

    @Test
    void 관리자가_아니면_강제_폐쇄를_철회할_수_없고_CSRF가_없으면_403() throws Exception {
        clock.setTo(PRESSED);
        Member admin = admin("관리자5");
        BlogFixture b = blog("force-guard", "PUBLIC");
        Member manager = member("철회부장");
        addMember(b.blogId(), manager.id(), "MANAGER");
        postAs(relogin(admin), "/api/admin/blogs/" + b.blogId() + "/force-close", null);
        String url = "/api/admin/blogs/" + b.blogId() + "/force-close";

        for (Member who : List.of(b.owner(), manager, member("철회회원"))) {
            Res r = deleteAs(relogin(who), url);
            assertThat(r.status()).isEqualTo(403);
            assertThat(r.code()).isEqualTo("ADMIN_ONLY");
        }
        assertThat(deleteAs(null, url).status()).isEqualTo(401);
        int noCsrf = mvc.perform(delete(url).cookie(relogin(admin).cookies())).andReturn().getResponse().getStatus();
        assertThat(noCsrf).isEqualTo(403);
        assertThat(status(b.blogId())).isEqualTo("CLOSING");
        assertThat(actions("RESTORE")).isEmpty();
        assertThat(deleteAs(relogin(admin), "/api/admin/blogs/999999/force-close").status()).isEqualTo(404);
    }

    @Test
    void 철회하지_않으면_예약_시각에_폐쇄된다() throws Exception {
        clock.setTo(PRESSED.minusHours(1));
        Member admin = admin("관리자2");
        BlogFixture b = blog("force-done", "PUBLIC");
        clock.setTo(PRESSED);
        postAs(relogin(admin), "/api/admin/blogs/" + b.blogId() + "/force-close", null);
        runAt(LocalDateTime.of(2026, 10, 9, 4, 0));
        assertThat(status(b.blogId())).isEqualTo("CLOSED");

        Res closed = postAs(relogin(admin), "/api/admin/blogs/" + b.blogId() + "/force-close", null);
        assertThat(closed.status()).isEqualTo(409);
        assertThat(closed.code()).isEqualTo("BLOG_ALREADY_CLOSED");
        assertThat(actions("BLOG_FORCE_CLOSE")).hasSize(1);
    }

    @Test
    void 이미_폐쇄_예정이면_시각과_알림은_그대로_두고_사유만_관리자로_바꾼다() throws Exception {
        clock.setTo(PRESSED.minusHours(1));
        Member admin = admin("관리자3");
        BlogFixture b = blog("force-kept", "PUBLIC");
        Member member = memberOf(b.blogId(), "유지멤버");
        clock.setTo(PRESSED);
        Res own = postAs(relogin(b.owner()), "/api/blogs/" + b.blogId() + "/closing", null);
        String scheduled = own.json().get("closeScheduledAt").asText();
        assertThat(closingCount(member)).isEqualTo(1);

        clock.setTo(PRESSED.plusDays(1));
        Res r = postAs(relogin(admin), "/api/admin/blogs/" + b.blogId() + "/force-close", null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("keptExisting").asBoolean()).isTrue();
        assertThat(r.json().get("closeScheduledAt").asText()).isEqualTo(scheduled);
        assertThat(jdbc.queryForObject("SELECT close_reason FROM blogs WHERE id = ?", String.class, b.blogId()))
                .isEqualTo("ADMIN");
        assertThat(closingCount(member)).isEqualTo(1);
        assertThat(actions("BLOG_FORCE_CLOSE")).hasSize(1);
        assertThat(postAs(relogin(admin), "/api/admin/blogs/999999/force-close", null).status()).isEqualTo(404);
    }
}
