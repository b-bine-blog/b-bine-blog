package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 강제 폐쇄 (005 T020, US2-3, FR-013, D-94, D-103 #8). */
class AdminForceCloseIntegrationTest extends AdminTestBase {

    private static final LocalDateTime PRESSED = LocalDateTime.of(2026, 10, 2, 15, 0);

    private long closingCount(Member m) {
        return notificationsOf(m.id()).stream().filter("BLOG_CLOSING"::equals).count();
    }

    private String status(long blog) {
        return jdbc.queryForObject("SELECT status FROM blogs WHERE id = ?", String.class, blog);
    }

    private void runAt(LocalDateTime at) {
        clock.setTo(at);
        runDaily();
    }

    @Test
    void 강제_폐쇄는_7일_뒤_04시_예약과_같은_알림을_따르고_블로그장이_철회할_수_있다() throws Exception {
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
        assertThat(jdbc.queryForObject("SELECT close_reason FROM blogs WHERE id = ?", String.class, b.blogId()))
                .isEqualTo("ADMIN");
        assertThat(closingCount(member)).isEqualTo(1);
        assertThat(closingCount(subscriber)).isEqualTo(1);
        assertThat(closingCount(b.owner())).isZero();
        assertThat(actions("BLOG_FORCE_CLOSE")).hasSize(1);

        runAt(LocalDateTime.of(2026, 10, 6, 4, 0));
        runAt(LocalDateTime.of(2026, 10, 8, 4, 0));
        assertThat(closingCount(member)).isEqualTo(3);
        assertThat(closingCount(subscriber)).isEqualTo(3);
        assertThat(closingCount(b.owner())).isZero();

        // 관리자는 블로그장의 철회 버튼을 쓸 수 없고, 블로그장은 철회할 수 있다(D-103 #8)
        assertThat(deleteAs(relogin(admin), "/api/blogs/" + b.blogId() + "/closing").status()).isEqualTo(403);
        Res withdraw = deleteAs(relogin(b.owner()), "/api/blogs/" + b.blogId() + "/closing");
        assertThat(withdraw.status()).isIn(200, 204);
        assertThat(notificationsOf(member.id())).contains("BLOG_CLOSING_WITHDRAWN");
        runAt(LocalDateTime.of(2026, 10, 9, 4, 0));
        assertThat(status(b.blogId())).isEqualTo("ACTIVE");
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
