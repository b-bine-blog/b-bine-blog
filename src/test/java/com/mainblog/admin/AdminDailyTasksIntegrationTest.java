package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 운영 기록 1년 보관과 04:00 정리 (005 T046, US6-2~4, FR-038, D-103 #19). */
class AdminDailyTasksIntegrationTest extends AdminTestBase {

    private long sanction(long blogId, long userId, long handledBy, String type, String period, LocalDateTime endsAt,
                          Long reportId) {
        jdbc.update("INSERT INTO blog_sanctions (blog_id, user_id, report_id, sanction_type, suspend_period, reason, "
                        + "starts_at, ends_at, handled_by, created_at) VALUES (?, ?, ?, ?, ?, '사유', ?, ?, ?, ?)",
                blogId, userId, reportId, type, period, clock.now(), endsAt, handledBy, clock.now());
        return jdbc.queryForObject("SELECT MAX(id) FROM blog_sanctions", Long.class);
    }

    private int rows(String table) {
        return count("SELECT COUNT(*) FROM " + table);
    }

    @Test
    void 일년_동안_남고_일년이_지나면_효력_있는_정지와_블랙리스트_원인만_남기고_지운다() throws Exception {
        LocalDateTime start = clock.now();
        Member admin = admin("정리관리자");
        BlogFixture b = blog("purge-records", "PUBLIC");
        Member m1 = memberOf(b.blogId(), "정리멤버1");
        Member m2 = memberOf(b.blogId(), "정리멤버2");
        Member m3 = memberOf(b.blogId(), "정리멤버3");
        Member mgr = member("정리부장");
        addMember(b.blogId(), mgr.id(), "MANAGER"); // 박탈 뒤 폐쇄되지 않게(폐쇄 30일 정리는 블랙리스트를 지운다)
        Member reporter = member("정리신고자");
        long report = insertAdminReport(b.blogId(), reporter.id(), "BLOG", b.blogId());
        long blogReport = insertAdminReport(b.blogId(), reporter.id(), "USER", m1.id());

        postAs(admin, "/api/admin/members/" + m1.id() + "/privacy-view", null);
        postAs(admin, "/api/admin/reports/" + report + "/resolution", "{\"result\":\"OWNER_WARNING\",\"reason\":\"1년 기록\"}");
        long warning = sanction(b.blogId(), m1.id(), b.owner().id(), "WARNING", null, null, null);
        long shortSuspend = sanction(b.blogId(), m2.id(), b.owner().id(), "SUSPEND", "DAYS_3",
                start.plusDays(3), null);
        long permanent = sanction(b.blogId(), m3.id(), b.owner().id(), "SUSPEND", "PERMANENT", null, blogReport);
        jdbc.update("INSERT INTO blog_sanctions (blog_id, user_id, sanction_type, starts_at, handled_by, created_at) "
                + "VALUES (?, ?, 'KICK', ?, ?, ?)", b.blogId(), m1.id(), start, b.owner().id(), start);
        long kick = jdbc.queryForObject("SELECT MAX(id) FROM blog_sanctions", Long.class);
        jdbc.update("INSERT INTO blog_blacklists (sanction_id, blog_id, name_hash, email_hash, phone_hash) "
                + "VALUES (?, ?, 'n', 'e', 'p')", kick, b.blogId());
        postAs(admin, "/api/admin/blogs/" + b.blogId() + "/owner-revocation", "{\"ownerId\":" + b.owner().id() + "}");
        withdraw(m1.id());

        int actions = rows("admin_actions");
        assertThat(actions).isGreaterThanOrEqualTo(4);
        assertThat(rows("owner_warnings")).isEqualTo(1);
        assertThat(rows("owner_revocations")).isEqualTo(1);
        assertThat(rows("blog_sanctions")).isEqualTo(4);

        // 31일 뒤: 30일 정리는 운영 기록을 건드리지 않는다. 탈퇴 회원의 개인정보만 지워진다
        clock.setTo(start.plusDays(31).withHour(4).withMinute(0));
        runDaily();
        assertThat(rows("admin_actions")).isEqualTo(actions);
        assertThat(rows("owner_warnings")).isEqualTo(1);
        assertThat(rows("owner_revocations")).isEqualTo(1);
        assertThat(rows("reports")).isEqualTo(2);
        assertThat(rows("blog_sanctions")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, m1.id())).isNull();
        JsonNode list = getAs(relogin(admin), "/api/admin/actions?actionType=PRIVACY_VIEW").json().get("items");
        assertThat(list.get(0).get("targetLabel").asText()).isEqualTo("탈퇴한 회원");

        // 366일 뒤: 1년 지난 기록은 지우고, 효력 있는 영구 정지와 블랙리스트가 가리키는 강제 퇴장만 남는다
        clock.setTo(start.plusDays(366).withHour(4).withMinute(0));
        runDaily();
        assertThat(rows("admin_actions")).isZero();
        assertThat(rows("owner_warnings")).isZero();
        assertThat(rows("owner_revocations")).isZero();
        assertThat(rows("reports")).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM blog_sanctions ORDER BY id", Long.class))
                .containsExactly(permanent, kick);
        assertThat(jdbc.queryForObject("SELECT report_id FROM blog_sanctions WHERE id = ?", Long.class, permanent))
                .isNull();
        assertThat(count("SELECT COUNT(*) FROM blog_sanctions WHERE id IN (?, ?)", warning, shortSuspend)).isZero();
        assertThat(rows("blog_blacklists")).isEqualTo(1);
    }

    @Test
    void 영구_정지를_풀었으면_일년_뒤에_지운다() throws Exception {
        LocalDateTime start = clock.now();
        BlogFixture b = blog("purge-unsuspend", "PUBLIC");
        Member m = memberOf(b.blogId(), "풀린멤버");
        sanction(b.blogId(), m.id(), b.owner().id(), "SUSPEND", "PERMANENT", null, null);
        sanction(b.blogId(), m.id(), b.owner().id(), "UNSUSPEND", null, null, null);
        clock.setTo(start.plusDays(366).withHour(4).withMinute(0));
        runDaily();
        assertThat(rows("blog_sanctions")).isZero();
    }
}
