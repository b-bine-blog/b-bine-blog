package com.mainblog.blog.application;

import com.mainblog.blog.application.SanctionService.Command;
import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.blog.domain.SanctionType;
import com.mainblog.blog.domain.SuspendPeriod;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 안 신고 목록·처리 (T063, BLG-13, SOC-06, FR-052, research R-11). route = 'BLOG'인 신고만 다룬다.
 * 블로그장에 대한 신고는 만들 때(003) ADMIN으로 가므로 여기 나오지 않는다(D-93).
 */
@Service
public class BlogReportService {

    /** 대상 회원: USER는 target_id, POST·COMMENT는 작성자. */
    private static final String SELECT = """
            SELECT r.id, r.target_type, r.target_id, r.target_snapshot, r.reason, r.reason_detail, r.status, r.result,
                   r.created_at, r.handled_at, r.reporter_id,
                   CASE r.target_type WHEN 'USER' THEN r.target_id
                        WHEN 'POST' THEN (SELECT p.author_id FROM posts p WHERE p.id = r.target_id)
                        WHEN 'COMMENT' THEN (SELECT c.author_id FROM comments c WHERE c.id = r.target_id) END AS target_user_id
            FROM reports r""";

    private final BlogRepository blogRepository;
    private final BlogPermissionService permissionService;
    private final SanctionService sanctionService;
    private final NotificationWriter notifications;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public BlogReportService(BlogRepository blogRepository, BlogPermissionService permissionService,
                             SanctionService sanctionService, NotificationWriter notifications, JdbcTemplate jdbc,
                             Clock clock) {
        this.blogRepository = blogRepository;
        this.permissionService = permissionService;
        this.sanctionService = sanctionService;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Paged<ReportView> list(long blogId, String status, int page, int size) {
        String s = status == null || status.isBlank() ? "PENDING" : status;
        if (!s.equals("PENDING") && !s.equals("DONE")) {
            throw new InvalidInputException("status", "Pattern");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE blog_id = ? AND route = 'BLOG' "
                + "AND status = ?", Long.class, blogId, s);
        List<ReportView> items = jdbc.query(SELECT + " WHERE r.blog_id = ? AND r.route = 'BLOG' AND r.status = ? "
                + "ORDER BY r.id DESC LIMIT ? OFFSET ?", this::map, blogId, s, size, (page - 1) * size);
        return new Paged<>(items, page, size, total == null ? 0 : total);
    }

    public record Resolution(String result, SuspendPeriod period, String reason) {}

    /** 문제 없음이면 기록만, 경고·정지·강퇴면 제재하고 report_id를 연결한다. 신고자에게 결과를 알린다. */
    @Transactional
    public ReportView resolve(long blogId, long reportId, long actorId, Resolution res) {
        Blog blog = blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        permissionService.require(blog, actorId, BlogPermission.MEMBER_MANAGE);
        List<ReportView> found = jdbc.query(SELECT + " WHERE r.id = ? AND r.blog_id = ? AND r.route = 'BLOG' FOR UPDATE",
                this::map, reportId, blogId);
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ReportView report = found.get(0);
        if ("DONE".equals(report.status())) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_HANDLED);
        }
        String result = res.result();
        if (result == null || !List.of("NONE", "WARNING", "SUSPEND", "KICK").contains(result)) {
            throw new InvalidInputException("result", "Pattern");
        }
        if (!"NONE".equals(result)) {
            if (report.targetUserId() == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            sanctionService.apply(blog, actorId, report.targetUserId(),
                    new Command(SanctionType.valueOf(result), res.period(), res.reason()), reportId);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("UPDATE reports SET status = 'DONE', result = ?, handled_by = ?, handled_at = ? WHERE id = ?",
                result, actorId, now, reportId);
        notifications.write(report.reporterId(), NotificationType.REPORT_RESOLVED, "BLOG", blogId, null,
                "'" + blog.getName() + "' 블로그에 한 신고가 처리되었습니다. 결과: " + label(result),
                "REPORT_RESOLVED:" + reportId);
        return jdbc.query(SELECT + " WHERE r.id = ?", this::map, reportId).get(0);
    }

    private static String label(String result) {
        return switch (result) {
            case "WARNING" -> "경고";
            case "SUSPEND" -> "정지";
            case "KICK" -> "강제 퇴장";
            default -> "문제 없음";
        };
    }

    private ReportView map(ResultSet rs, int i) throws SQLException {
        Long target = rs.getObject("target_user_id") == null ? null : rs.getLong("target_user_id");
        String nickname = target == null ? null : jdbc.query("SELECT nickname FROM users WHERE id = ?",
                r -> r.next() ? r.getString(1) : null, target);
        return new ReportView(rs.getLong("id"), rs.getString("target_type"), rs.getLong("target_id"), target,
                nickname, rs.getString("target_snapshot"), rs.getString("reason"), rs.getString("reason_detail"),
                rs.getString("status"), rs.getString("result"), rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("handled_at", LocalDateTime.class), rs.getLong("reporter_id"));
    }

    /** reporterId는 응답에 넣지 않는다(신고자 보호). */
    public record ReportView(Long id, String targetType, Long targetId, Long targetUserId, String targetUserNickname,
                             String targetSnapshot, String reason, String reasonDetail, String status, String result,
                             LocalDateTime createdAt, LocalDateTime handledAt,
                             @com.fasterxml.jackson.annotation.JsonIgnore Long reporterId) {}
}
