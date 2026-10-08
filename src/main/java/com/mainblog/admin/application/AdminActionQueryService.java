package com.mainblog.admin.application;

import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.admin.application.AdminViews.UserRef;
import com.mainblog.common.error.InvalidInputException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 활동 기록 보기 (005 T047, ADM-06, FR-039, SEC-09, D-103 #20, research R-06).
 * 모든 관리자가 본다. 거르기: 종류·관리자·대상(종류 + 번호)·기간(날짜). 최신순. 고치기·지우기 API는 없다.
 * 대상 이름: 회원 닉네임·블로그 이름·글 제목·댓글/신고 번호. 탈퇴 회원은 "탈퇴한 회원", 지워진 대상은 "삭제된 대상".
 */
@Service
public class AdminActionQueryService {

    public static final String DELETED_TARGET = "삭제된 대상";

    public record AdminAction(long id, UserRef admin, String actionType, String targetType, long targetId,
                              String targetLabel, Long reportId, String memo, LocalDateTime createdAt) {}

    private static final String LABEL = """
            CASE a.target_type
              WHEN 'USER' THEN (SELECT CASE WHEN tu.status = 'ACTIVE' THEN tu.nickname ELSE ? END
                                FROM users tu WHERE tu.id = a.target_id)
              WHEN 'BLOG' THEN (SELECT tb.name FROM blogs tb WHERE tb.id = a.target_id)
              WHEN 'POST' THEN (SELECT tp.title FROM posts tp WHERE tp.id = a.target_id)
              WHEN 'COMMENT' THEN (SELECT CONCAT('댓글 #', tc.id) FROM comments tc WHERE tc.id = a.target_id)
              WHEN 'REPORT' THEN (SELECT CONCAT('신고 #', tr.id) FROM reports tr WHERE tr.id = a.target_id)
            END""";

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final AdminPaging paging;

    public AdminActionQueryService(JdbcTemplate jdbc, AdminGuard guard, AdminPaging paging) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.paging = paging;
    }

    @Transactional(readOnly = true)
    public Page<AdminAction> list(long adminId, String actionType, Long filterAdminId, String targetType, Long targetId,
                                  String from, String to, Integer page, Integer size) {
        guard.require(adminId);
        int p = paging.page(page);
        int n = paging.size(size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (actionType != null && !actionType.isBlank()) {
            if (Arrays.stream(ActionType.values()).noneMatch(t -> t.name().equals(actionType))) {
                throw new InvalidInputException("actionType", "Pattern");
            }
            where.append(" AND a.action_type = ?");
            args.add(actionType);
        }
        if (filterAdminId != null) {
            where.append(" AND a.admin_id = ?");
            args.add(filterAdminId);
        }
        if (targetType != null && !targetType.isBlank()) {
            if (Arrays.stream(TargetType.values()).noneMatch(t -> t.name().equals(targetType))) {
                throw new InvalidInputException("targetType", "Pattern");
            }
            where.append(" AND a.target_type = ?");
            args.add(targetType);
        }
        if (targetId != null) {
            where.append(" AND a.target_id = ?");
            args.add(targetId);
        }
        LocalDate fromDate = date("from", from);
        LocalDate toDate = date("to", to);
        if (fromDate != null) {
            where.append(" AND a.created_at >= ?");
            args.add(fromDate.atStartOfDay());
        }
        if (toDate != null) {
            where.append(" AND a.created_at < ?");
            args.add(toDate.plusDays(1).atStartOfDay());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions a" + where, Long.class, args.toArray());
        List<Object> all = new ArrayList<>();
        all.add(AdminViews.WITHDRAWN_NAME);
        all.addAll(args);
        all.add(n);
        all.add(AdminPaging.offset(p, n));
        List<AdminAction> items = jdbc.query("SELECT a.id, a.admin_id, au.nickname AS admin_nickname, "
                + "au.status AS admin_status, a.action_type, a.target_type, a.target_id, a.report_id, a.memo, "
                + "a.created_at, " + LABEL + " AS target_label FROM admin_actions a "
                + "LEFT JOIN users au ON au.id = a.admin_id" + where + " ORDER BY a.created_at DESC, a.id DESC "
                + "LIMIT ? OFFSET ?", (rs, i) -> {
            String label = rs.getString("target_label");
            return new AdminAction(rs.getLong("id"), UserRef.of(rs.getLong("admin_id"), rs.getString("admin_nickname"),
                    rs.getString("admin_status")), rs.getString("action_type"), rs.getString("target_type"),
                    rs.getLong("target_id"), label == null ? DELETED_TARGET : label,
                    rs.getObject("report_id", Long.class), rs.getString("memo"),
                    rs.getObject("created_at", LocalDateTime.class));
        }, all.toArray());
        return Page.of(items, p, n, total == null ? 0 : total);
    }

    private static LocalDate date(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(field, "Pattern");
        }
    }
}
