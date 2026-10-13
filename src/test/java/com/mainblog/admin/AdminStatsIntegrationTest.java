package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.mainblog.admin.application.AdminGuard;
import com.mainblog.admin.application.AdminStatsService;
import com.mainblog.common.config.AdminProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 운영 통계 (005 T050, US7-1~3, FR-040, D-103 #21). */
class AdminStatsIntegrationTest extends AdminTestBase {

    @Autowired
    AdminProperties props;

    @Autowired
    AdminGuard guard;

    @Autowired
    Clock appClock;

    private static final LocalDateTime D1 = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime D2 = LocalDateTime.of(2026, 10, 2, 10, 0);

    private void joinedAt(Member m, LocalDateTime at) {
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", at, m.id());
    }

    private int countOn(JsonNode daily, String date) {
        for (JsonNode d : daily) {
            if (d.get("date").asText().equals(date)) {
                return d.get("count").asInt();
            }
        }
        throw new AssertionError("날짜 없음: " + date);
    }

    @Test
    void 회원_블로그_수와_일별_가입_글_수를_센다() throws Exception {
        clock.setTo(D2);
        Member admin = admin("통계관리자");
        joinedAt(admin, D2);
        List<Member> day1 = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Member m = member("첫날" + i);
            joinedAt(m, D1);
            day1.add(m);
        }
        List<Member> day2 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Member m = member("둘째날" + i);
            joinedAt(m, D2);
            day2.add(m);
        }
        long b1 = createBlog(day1.get(0), "stats-1", "PUBLIC", "FREE");
        long b2 = createBlog(day1.get(1), "stats-2", "PRIVATE", "FREE");
        long b3 = createBlog(day1.get(2), "stats-3", "PUBLIC", "FREE");
        long b4 = createBlog(day2.get(0), "stats-4", "PUBLIC", "FREE");
        long b5 = createBlog(day2.get(1), "stats-5", "PUBLIC", "FREE");
        jdbc.update("UPDATE blogs SET hidden = TRUE, hidden_at = ? WHERE id = ?", D2, b2);
        jdbc.update("UPDATE blogs SET status = 'CLOSING', close_scheduled_at = ?, close_reason = 'OWNER' WHERE id = ?",
                D2.plusDays(7), b3);
        jdbc.update("UPDATE blogs SET status = 'CLOSED', closed_at = ? WHERE id = ?", D2, b5);
        for (int i = 0; i < 7; i++) {
            insertPost(b1, day1.get(0).id(), "글" + i, "본문", D2);
        }
        insertPost(b4, day2.get(0).id(), "첫날글", "본문", D1);
        jdbc.update("UPDATE posts SET status = 'DELETED', deleted_at = ? WHERE title = '글0'", D2);
        jdbc.update("UPDATE posts SET status = 'HIDDEN', hidden_at = ? WHERE title = '글1'", D2);
        jdbc.update("INSERT INTO posts (blog_id, author_id, board_type, title, content, status, created_at, updated_at) "
                + "VALUES (NULL, ?, 'NOTICE', '공지', '본문', 'VISIBLE', ?, ?)", admin.id(), D2, D2);

        Res r = getAs(admin, "/api/admin/stats");
        assertThat(r.status()).isEqualTo(200);
        JsonNode s = r.json();
        assertThat(s.get("memberCount").asLong()).isEqualTo(8);
        assertThat(s.get("blogCount").asLong()).isEqualTo(4);
        assertThat(s.get("dailySignups")).hasSize(30);
        assertThat(s.get("dailySignups").get(29).get("date").asText()).isEqualTo("2026-10-02");
        assertThat(s.get("dailySignups").get(0).get("date").asText()).isEqualTo("2026-09-03");
        assertThat(countOn(s.get("dailySignups"), "2026-10-01")).isEqualTo(3);
        assertThat(countOn(s.get("dailySignups"), "2026-10-02")).isEqualTo(5);
        assertThat(countOn(s.get("dailySignups"), "2026-09-30")).isZero();
        assertThat(countOn(s.get("dailyPosts"), "2026-10-02")).isEqualTo(7);
        assertThat(countOn(s.get("dailyPosts"), "2026-10-01")).isEqualTo(1);

        withdraw(day2.get(4).id());
        JsonNode after = getAs(admin, "/api/admin/stats").json();
        assertThat(after.get("memberCount").asLong()).isEqualTo(7);
        assertThat(countOn(after.get("dailySignups"), "2026-10-02")).isEqualTo(5);

        assertThat(getAs(day1.get(0), "/api/admin/stats").status()).isEqualTo(403);
        assertThat(getAs(null, "/api/admin/stats").status()).isEqualTo(401);
    }

    @Test
    void 일별_기간은_설정값을_따른다() throws Exception {
        clock.setTo(D2);
        long adminId = admin("설정관리자").id();
        AdminProperties seven = new AdminProperties(props.list(), props.member(), props.privacyView(), props.warning(),
                props.revocation(), props.records(), new AdminProperties.Stats(7));
        AdminStatsService service = new AdminStatsService(jdbc, guard, seven, appClock);
        AdminStatsService.Stats s = service.stats(adminId);
        assertThat(s.dailySignups()).hasSize(7);
        assertThat(s.dailyPosts()).hasSize(7);
        assertThat(s.dailySignups().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 26));
    }
}
