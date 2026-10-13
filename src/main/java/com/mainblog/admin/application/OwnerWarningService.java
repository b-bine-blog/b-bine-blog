package com.mainblog.admin.application;

import com.mainblog.admin.application.AdminMemberService.OwnerWarning;
import com.mainblog.admin.application.AdminViews.UserRef;
import com.mainblog.admin.application.OwnerRevocationService.Revocation;
import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.common.config.AdminProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그장 경고 (005 T030, ADM-07, FR-020, FR-021, D-30, D-95, D-103 #12·#13, research R-09).
 * 잠금 → ownerId 확인 → 사유 검사 → owner_warnings → 1년 안 경고 수 → OWNER_WARNED 알림 → 3번이면 박탈(WARNING_3).
 * 경고는 블로그별·그 사람 기준으로 센다. 새 블로그장은 0부터, 전 블로그장이 돌아오면 자기 경고를 다시 센다.
 */
@Service
public class OwnerWarningService {

    public record WarningResult(OwnerWarning warning, int activeCount, int threshold, Revocation revocation) {}

    public record WarningList(List<OwnerWarning> warnings) {}

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final AdminActionLog actionLog;
    private final AdminBlogService blogService;
    private final OwnerRevocationService revocationService;
    private final BlogRepository blogRepository;
    private final NotificationWriter notifications;
    private final AdminProperties props;
    private final Clock clock;

    public OwnerWarningService(JdbcTemplate jdbc, AdminGuard guard, AdminActionLog actionLog,
                               AdminBlogService blogService, OwnerRevocationService revocationService,
                               BlogRepository blogRepository, NotificationWriter notifications,
                               AdminProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.actionLog = actionLog;
        this.blogService = blogService;
        this.revocationService = revocationService;
        this.blogRepository = blogRepository;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    /** 이 블로그의 경고 기록(1년 보관 중인 것), 최근 순. 1년 안이면 counted. */
    @Transactional(readOnly = true)
    public WarningList list(long adminId, long blogId) {
        guard.require(adminId);
        if (!blogRepository.existsById(blogId)) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        LocalDateTime since = LocalDateTime.now(clock).minus(props.warning().window());
        List<OwnerWarning> rows = jdbc.query("""
                SELECT w.id, w.user_id, ou.nickname AS owner_nickname, ou.status AS owner_status, w.reason,
                       w.admin_id, au.nickname AS admin_nickname, au.status AS admin_status, w.report_id, w.created_at
                FROM owner_warnings w JOIN users ou ON ou.id = w.user_id JOIN users au ON au.id = w.admin_id
                WHERE w.blog_id = ? ORDER BY w.created_at DESC, w.id DESC""", (rs, i) -> {
            LocalDateTime at = rs.getTimestamp("created_at").toLocalDateTime();
            return new OwnerWarning(rs.getLong("id"),
                    UserRef.of(rs.getLong("user_id"), rs.getString("owner_nickname"), rs.getString("owner_status")),
                    rs.getString("reason"),
                    UserRef.of(rs.getLong("admin_id"), rs.getString("admin_nickname"), rs.getString("admin_status")),
                    rs.getObject("report_id", Long.class), at, at.isAfter(since));
        }, blogId);
        return new WarningList(rows);
    }

    @Transactional
    public WarningResult warn(long adminId, long blogId, Long ownerId, String reason) {
        guard.require(adminId);
        String r = requiredReason(reason);
        if (ownerId == null) {
            throw new InvalidInputException("ownerId", "Required");
        }
        Blog blog = blogService.lockOpen(blogId);
        revocationService.requireOwner(blogId, ownerId);
        return warnLocked(adminId, blog, ownerId, r, null);
    }

    /** 사유: 앞뒤 공백 제거 후 1~500자(admin.warning.reason-max-length, D-103 #12). */
    String requiredReason(String reason) {
        String r = reason == null ? "" : reason.strip();
        if (r.isEmpty()) {
            throw new InvalidInputException("reason", "Required");
        }
        if (r.length() > props.warning().reasonMaxLength()) {
            throw new InvalidInputException("reason", "Size");
        }
        return r;
    }

    /** 경고 한 번. 호출하는 쪽이 블로그 행을 잠그고 ownerId를 확인했다(신고 처리도 부른다). */
    @Transactional(propagation = Propagation.MANDATORY)
    public WarningResult warnLocked(long adminId, Blog blog, long ownerId, String reason, Long reportId) {
        LocalDateTime now = LocalDateTime.now(clock);
        long blogId = blog.getId();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO owner_warnings (blog_id, user_id, report_id, admin_id, reason, "
                    + "created_at) VALUES (?, ?, ?, ?, ?, ?)", new String[] {"id"});
            ps.setLong(1, blogId);
            ps.setLong(2, ownerId);
            ps.setObject(3, reportId);
            ps.setLong(4, adminId);
            ps.setString(5, reason);
            ps.setObject(6, now);
            return ps;
        }, key);
        long warningId = key.getKey().longValue();
        actionLog.write(adminId, ActionType.OWNER_WARNING, TargetType.USER, ownerId, reportId,
                "블로그 " + blogId + ": " + reason);

        int active = blogService.activeWarningCount(blogId, ownerId);
        int threshold = props.warning().revokeThreshold();
        notifications.write(ownerId, NotificationType.OWNER_WARNED, "BLOG", blogId, null,
                "'" + blog.getName() + "' 블로그장 경고 (" + threshold + "번 중 " + Math.min(active, threshold)
                        + "번). 사유: " + reason, "OWNER_WARNED:" + warningId);

        Revocation revocation = active >= threshold
                ? revocationService.revoke(adminId, blog, ownerId, OwnerRevocationService.WARNING_3, reportId, null)
                : null;
        OwnerWarning view = new OwnerWarning(warningId, user(ownerId), reason, user(adminId), reportId, now, true);
        return new WarningResult(view, active, threshold, revocation);
    }

    private UserRef user(long id) {
        return jdbc.query("SELECT nickname, status FROM users WHERE id = ?",
                rs -> rs.next() ? UserRef.of(id, rs.getString(1), rs.getString(2)) : null, id);
    }
}
