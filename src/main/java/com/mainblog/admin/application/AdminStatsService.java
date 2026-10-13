package com.mainblog.admin.application;

import com.mainblog.common.config.AdminProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 통계 (005 T051, ADM-05, FR-040, D-103 #21, research R-12, data-model "통계 쿼리").
 * 회원 수는 탈퇴·관리자 제외, 블로그 수는 운영 중·폐쇄 예정(숨김 포함). 일별 가입·글 수는 오늘 포함 최근
 * admin.stats.daily-days일, 그날 생긴 수를 그대로 센다(나중에 탈퇴·삭제·숨김돼도). 빈 날은 0. 공지는 빼고 블로그 글만.
 */
@Service
public class AdminStatsService {

    public record DailyCount(LocalDate date, int count) {}

    public record Stats(long memberCount, long blogCount, List<DailyCount> dailySignups, List<DailyCount> dailyPosts) {}

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final AdminProperties props;
    private final Clock clock;

    public AdminStatsService(JdbcTemplate jdbc, AdminGuard guard, AdminProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Stats stats(long adminId) {
        guard.require(adminId);
        Long members = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE status = 'ACTIVE' AND role = 'USER'",
                Long.class);
        Long blogs = jdbc.queryForObject("SELECT COUNT(*) FROM blogs WHERE status IN ('ACTIVE', 'CLOSING')",
                Long.class);
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(props.stats().dailyDays() - 1L);
        return new Stats(members == null ? 0 : members, blogs == null ? 0 : blogs,
                daily("SELECT DATE(created_at) AS d, COUNT(*) AS n FROM users WHERE role = 'USER' "
                        + "AND created_at >= ? AND created_at < ? GROUP BY DATE(created_at)", start, today),
                daily("SELECT DATE(created_at) AS d, COUNT(*) AS n FROM posts WHERE board_type = 'BLOG' "
                        + "AND created_at >= ? AND created_at < ? GROUP BY DATE(created_at)", start, today));
    }

    private List<DailyCount> daily(String sql, LocalDate start, LocalDate today) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        jdbc.query(sql, rs -> {
            counts.put(rs.getObject("d", LocalDate.class), rs.getInt("n"));
        }, start.atStartOfDay(), today.plusDays(1).atStartOfDay());
        List<DailyCount> out = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            out.add(new DailyCount(d, counts.getOrDefault(d, 0)));
        }
        return out;
    }
}
