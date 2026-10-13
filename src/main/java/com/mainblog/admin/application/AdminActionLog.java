package com.mainblog.admin.application;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 활동 기록 쓰기 (005 T008, FR-039, ADM-06, SC-004, research R-06).
 * 조치와 같은 트랜잭션에서만 쓴다(MANDATORY). 조치가 되돌려지면 기록도 남지 않고, 기록이 실패하면 조치도 되돌려진다.
 * 고치거나 지우는 메서드는 두지 않는다(D-103 #20). memo는 500자로 자르고 이메일·전화번호 원문은 넣지 않는다(D-22).
 */
@Component
public class AdminActionLog {

    static final int MEMO_MAX = 500;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdminActionLog(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long write(long adminId, ActionType type, TargetType targetType, long targetId, Long reportId, String memo) {
        String text = memo == null || memo.isBlank() ? null : truncate(memo.strip());
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO admin_actions (admin_id, report_id, action_type, "
                    + "target_type, target_id, memo, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, adminId);
            ps.setObject(2, reportId);
            ps.setString(3, type.name());
            ps.setString(4, targetType.name());
            ps.setLong(5, targetId);
            ps.setString(6, text);
            ps.setTimestamp(7, now);
            ps.setTimestamp(8, now);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    static String truncate(String s) {
        return s.codePointCount(0, s.length()) <= MEMO_MAX ? s : s.substring(0, s.offsetByCodePoints(0, MEMO_MAX));
    }
}
