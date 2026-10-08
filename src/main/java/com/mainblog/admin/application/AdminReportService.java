package com.mainblog.admin.application;

import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.admin.application.AdminViews.UserRef;
import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.social.application.ReportResultNotifier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 신고함과 신고 처리 (005 T034, ADM-04, FR-030~FR-034, D-45, D-85, D-93, D-102 #14~#16, D-103 #18, research R-10).
 * route = ADMIN인 신고만 다룬다(블로그 자체·블로그장·블로그장이 쓴 글·댓글·메인 프로필 회원). 처리 효과는 경고·강퇴·강제 폐쇄
 * API와 같은 서비스를 부른다. 잠금 순서는 블로그 행 → 신고 행(교착 방지). 신고 수로 자동 조치하는 코드는 없다(FR-032).
 */
@Service
public class AdminReportService {

    public static final List<String> RESULTS = List.of("NONE", "OWNER_WARNING", "OWNER_KICK", "BLOG_CLOSE");

    public record BlogRef(long id, String name, String status) {}

    public record ReportSummary(long id, String targetType, long targetId, BlogRef blog, String reason, String status,
                                String result, LocalDateTime createdAt, LocalDateTime handledAt) {}

    public record ReportDetail(long id, String targetType, long targetId, BlogRef blog, String reason, String status,
                               String result, LocalDateTime createdAt, LocalDateTime handledAt, String targetSnapshot,
                               String reasonDetail, UserRef reporter, UserRef targetOwner,
                               boolean targetOwnerIsCurrentOwner, UserRef handledBy) {}

    private static final String SELECT = """
            SELECT r.id, r.target_type, r.target_id, r.blog_id, b.name AS blog_name, b.status AS blog_status,
                   r.reason, r.status, r.result, r.created_at, r.handled_at
            FROM reports r LEFT JOIN blogs b ON b.id = r.blog_id
            """;

    /** 처리 대상 회원: BLOG는 지금 블로그장, USER는 target_id, POST·COMMENT는 작성자. */
    private static final String TARGET_OWNER = """
            CASE r.target_type
                 WHEN 'BLOG' THEN (SELECT m.user_id FROM blog_members m WHERE m.blog_id = r.blog_id AND m.role = 'OWNER')
                 WHEN 'USER' THEN r.target_id
                 WHEN 'POST' THEN (SELECT p.author_id FROM posts p WHERE p.id = r.target_id)
                 WHEN 'COMMENT' THEN (SELECT c.author_id FROM comments c WHERE c.id = r.target_id) END""";

    private record Row(long id, Long blogId, String status, String targetType, Long targetOwnerId) {}

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final AdminActionLog actionLog;
    private final AdminPaging paging;
    private final AdminBlogService blogService;
    private final OwnerWarningService warningService;
    private final OwnerRevocationService revocationService;
    private final BlogRepository blogRepository;
    private final ReportResultNotifier resultNotifier;
    private final Clock clock;

    public AdminReportService(JdbcTemplate jdbc, AdminGuard guard, AdminActionLog actionLog, AdminPaging paging,
                              AdminBlogService blogService, OwnerWarningService warningService,
                              OwnerRevocationService revocationService, BlogRepository blogRepository,
                              ReportResultNotifier resultNotifier, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.actionLog = actionLog;
        this.paging = paging;
        this.blogService = blogService;
        this.warningService = warningService;
        this.revocationService = revocationService;
        this.blogRepository = blogRepository;
        this.resultNotifier = resultNotifier;
        this.clock = clock;
    }

    /** 대기 중은 오래된 순(먼저 온 것부터), 처리됨은 최근 처리 순(research R-10 가정). */
    @Transactional(readOnly = true)
    public Page<ReportSummary> list(long adminId, String status, Integer page, Integer size) {
        guard.require(adminId);
        String s = status == null || status.isBlank() ? "PENDING" : status;
        if (!s.equals("PENDING") && !s.equals("DONE")) {
            throw new InvalidInputException("status", "Pattern");
        }
        int p = paging.page(page);
        int n = paging.size(size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE route = 'ADMIN' AND status = ?",
                Long.class, s);
        String order = s.equals("PENDING") ? "r.created_at ASC, r.id ASC" : "r.handled_at DESC, r.id DESC";
        List<ReportSummary> items = jdbc.query(SELECT + " WHERE r.route = 'ADMIN' AND r.status = ? ORDER BY " + order
                + " LIMIT ? OFFSET ?", AdminReportService::summary, s, n, AdminPaging.offset(p, n));
        return Page.of(items, p, n, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ReportDetail detail(long adminId, long reportId) {
        guard.require(adminId);
        return load(reportId);
    }

    /**
     * 한 신고에 결과 하나, 바꿀 수 없다(FR-034, D-103 #18). 처리 대상이 지금 블로그장이 아니면 409 OWNER_CHANGED.
     * 블로그가 없는 신고(메인 프로필 회원 신고)는 NONE만 받는다(research R-10 가정, D-44).
     */
    @Transactional
    public ReportDetail resolve(long adminId, long reportId, String result, String reason) {
        guard.require(adminId);
        if (result == null || !RESULTS.contains(result)) {
            throw new InvalidInputException("result", "Pattern");
        }
        String warningReason = "OWNER_WARNING".equals(result) ? warningService.requiredReason(reason) : null;
        String kickReason = "OWNER_KICK".equals(result) ? revocationService.optionalReason(reason) : null;

        Row first = row(reportId, false);
        Blog blog = null;
        if (first.blogId() == null) {
            if (!"NONE".equals(result)) {
                throw new InvalidInputException("result", "Pattern");
            }
        } else if ("NONE".equals(result)) {
            blog = blogRepository.findByIdForUpdate(first.blogId()).orElse(null); // 잠금 순서만 맞춘다
        } else {
            blog = blogService.lockOpen(first.blogId());
        }
        Row report = row(reportId, true);
        if ("DONE".equals(report.status())) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_HANDLED);
        }

        if (!"NONE".equals(result)) {
            Long owner = blogService.currentOwner(blog.getId());
            if (owner == null || report.targetOwnerId() == null || !owner.equals(report.targetOwnerId())) {
                throw new BusinessException(ErrorCode.OWNER_CHANGED);
            }
            switch (result) {
                case "OWNER_WARNING" -> warningService.warnLocked(adminId, blog, owner, warningReason, reportId);
                case "OWNER_KICK" -> revocationService.revoke(adminId, blog, owner, OwnerRevocationService.DIRECT_KICK,
                        reportId, kickReason);
                default -> blogService.forceClose(adminId, blog.getId(), reportId);
            }
        }
        jdbc.update("UPDATE reports SET status = 'DONE', result = ?, handled_by = ?, handled_at = ? WHERE id = ?",
                result, adminId, LocalDateTime.now(clock), reportId);
        actionLog.write(adminId, ActionType.REPORT_HANDLE, TargetType.REPORT, reportId, reportId,
                ReportResultNotifier.resultLabel(result));
        resultNotifier.resolved(reportId); // 신고자가 "신고 처리 결과"를 껐으면 만들지 않는다(FR-033)
        return load(reportId);
    }

    private Row row(long reportId, boolean lock) {
        List<Row> rows = jdbc.query("SELECT r.id, r.blog_id, r.status, r.target_type, " + TARGET_OWNER
                        + " AS target_owner_id FROM reports r WHERE r.id = ? AND r.route = 'ADMIN'"
                        + (lock ? " FOR UPDATE" : ""),
                (rs, i) -> new Row(rs.getLong("id"), rs.getObject("blog_id", Long.class), rs.getString("status"),
                        rs.getString("target_type"), rs.getObject("target_owner_id", Long.class)), reportId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return rows.get(0);
    }

    private ReportDetail load(long reportId) {
        List<ReportDetail> rows = jdbc.query("""
                SELECT r.id, r.target_type, r.target_id, r.blog_id, b.name AS blog_name, b.status AS blog_status,
                       r.reason, r.status, r.result, r.created_at, r.handled_at, r.target_snapshot, r.reason_detail,
                       r.reporter_id, ru.nickname AS reporter_nickname, ru.status AS reporter_status,
                       r.handled_by, hu.nickname AS handled_nickname, hu.status AS handled_status,
                       (SELECT m.user_id FROM blog_members m WHERE m.blog_id = r.blog_id AND m.role = 'OWNER') AS current_owner,
                       """ + TARGET_OWNER + """
                 AS target_owner_id
                FROM reports r LEFT JOIN blogs b ON b.id = r.blog_id
                JOIN users ru ON ru.id = r.reporter_id LEFT JOIN users hu ON hu.id = r.handled_by
                WHERE r.id = ? AND r.route = 'ADMIN'""", (rs, i) -> {
            ReportSummary s = summary(rs, i);
            Long targetOwner = rs.getObject("target_owner_id", Long.class);
            Long current = rs.getObject("current_owner", Long.class);
            return new ReportDetail(s.id(), s.targetType(), s.targetId(), s.blog(), s.reason(), s.status(), s.result(),
                    s.createdAt(), s.handledAt(), rs.getString("target_snapshot"), rs.getString("reason_detail"),
                    UserRef.of(rs.getLong("reporter_id"), rs.getString("reporter_nickname"),
                            rs.getString("reporter_status")),
                    user(targetOwner), targetOwner != null && targetOwner.equals(current),
                    UserRef.of(rs.getObject("handled_by", Long.class), rs.getString("handled_nickname"),
                            rs.getString("handled_status")));
        }, reportId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return rows.get(0);
    }

    private UserRef user(Long id) {
        if (id == null) {
            return null;
        }
        return jdbc.query("SELECT nickname, status FROM users WHERE id = ?",
                rs -> rs.next() ? UserRef.of(id, rs.getString(1), rs.getString(2)) : null, id);
    }

    private static ReportSummary summary(ResultSet rs, int i) throws SQLException {
        Long blogId = rs.getObject("blog_id", Long.class);
        BlogRef blog = blogId == null ? null
                : new BlogRef(blogId, rs.getString("blog_name"), rs.getString("blog_status"));
        return new ReportSummary(rs.getLong("id"), rs.getString("target_type"), rs.getLong("target_id"), blog,
                rs.getString("reason"), rs.getString("status"), rs.getString("result"),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("handled_at", LocalDateTime.class));
    }
}
