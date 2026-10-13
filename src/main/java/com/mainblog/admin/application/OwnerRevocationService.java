package com.mainblog.admin.application;

import com.mainblog.admin.application.AdminViews.UserRef;
import com.mainblog.blog.application.OwnerSuccessionService;
import com.mainblog.blog.domain.Blog;
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
 * 블로그장 권한 박탈 (005 T029, ADM-07, FR-022~FR-029, D-17, D-54, D-94, D-103 #14~#17, research R-09).
 * 블로그 행 잠금 안에서 위임 요청 자동 취소 → 002 OwnerSuccessionService.revokeOwner(자동 위임 또는 폐쇄 예약)
 * → owner_revocations → OWNER_REVOKED 알림 → 활동 기록을 한 번에 한다. 지금 블로그장이 요청의 ownerId가 아니면
 * 409 OWNER_CHANGED라서 여러 관리자가 동시에 눌러도 박탈은 블로그당 한 번만 일어난다(FR-028, SC-005).
 */
@Service
public class OwnerRevocationService {

    public static final String WARNING_3 = "WARNING_3";
    public static final String DIRECT_KICK = "DIRECT_KICK";

    public record Revocation(long id, String cause, UserRef revokedUser, UserRef newOwner,
                             LocalDateTime closeScheduledAt, int canceledDelegations) {}

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final AdminActionLog actionLog;
    private final AdminBlogService blogService;
    private final OwnerSuccessionService successionService;
    private final NotificationWriter notifications;
    private final AdminProperties props;
    private final Clock clock;

    public OwnerRevocationService(JdbcTemplate jdbc, AdminGuard guard, AdminActionLog actionLog,
                                  AdminBlogService blogService, OwnerSuccessionService successionService,
                                  NotificationWriter notifications, AdminProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.actionLog = actionLog;
        this.blogService = blogService;
        this.successionService = successionService;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    /** 바로 강퇴(FR-022). 사유는 선택 0~500자(research R-09 가정). */
    @Transactional
    public Revocation revokeDirect(long adminId, long blogId, Long ownerId, String reason) {
        guard.require(adminId);
        String memo = optionalReason(reason);
        if (ownerId == null) {
            throw new InvalidInputException("ownerId", "Required");
        }
        Blog blog = blogService.lockOpen(blogId);
        requireOwner(blogId, ownerId);
        return revoke(adminId, blog, ownerId, DIRECT_KICK, null, memo);
    }

    /** 지금 블로그장이 ownerId인지 잠금 읽기로 확인한다. 아니면(바뀌었거나 박탈되어 없음) 409 OWNER_CHANGED. */
    void requireOwner(long blogId, long ownerId) {
        Long current = blogService.currentOwner(blogId);
        if (current == null || current != ownerId) {
            throw new BusinessException(ErrorCode.OWNER_CHANGED);
        }
    }

    String optionalReason(String reason) {
        if (reason == null || reason.strip().isEmpty()) {
            return null;
        }
        String r = reason.strip();
        if (r.length() > props.revocation().reasonMaxLength()) {
            throw new InvalidInputException("reason", "Size");
        }
        return r;
    }

    /**
     * 박탈 한 번의 처리(data-model "박탈 순서"). 호출하는 쪽이 같은 트랜잭션에서 블로그 행을 잠그고 ownerId를 확인했다.
     * 경고 3번(OwnerWarningService)·신고 처리(AdminReportService)도 이 메서드를 부른다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Revocation revoke(long adminId, Blog blog, long ownerId, String cause, Long reportId, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        long blogId = blog.getId();
        String blogName = blog.getName();
        int canceled = cancelPendingDelegations(blogId, blogName, now);

        OwnerSuccessionService.Result result = successionService.revokeOwner(blogId, adminId);

        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO owner_revocations (blog_id, user_id, cause, new_owner_id, "
                    + "close_scheduled_at, admin_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", new String[] {"id"});
            ps.setLong(1, blogId);
            ps.setLong(2, ownerId);
            ps.setString(3, cause);
            ps.setObject(4, result.newOwnerId());
            ps.setObject(5, result.closeScheduledAt());
            ps.setLong(6, adminId);
            ps.setObject(7, now);
            return ps;
        }, key);
        long revocationId = key.getKey().longValue();

        String why = WARNING_3.equals(cause) ? "경고 3번" : "바로 강퇴";
        String content = "'" + blogName + "' 블로그장 권한이 박탈되었습니다(" + why + "). 이 블로그의 일반 멤버로 남습니다."
                + (reason == null ? "" : " 사유: " + reason);
        notifications.write(ownerId, NotificationType.OWNER_REVOKED, "BLOG", blogId, null, content,
                "OWNER_REVOKED:" + revocationId);

        actionLog.write(adminId, ActionType.OWNER_KICK, TargetType.USER, ownerId, reportId,
                "블로그 " + blogId + ": " + why + (reason == null ? "" : ": " + reason));
        if (result.newOwnerId() != null) {
            actionLog.write(adminId, ActionType.AUTO_DELEGATE, TargetType.BLOG, blogId, reportId,
                    "새 블로그장 회원 " + result.newOwnerId());
        } else {
            actionLog.write(adminId, ActionType.CLOSE_SCHEDULE, TargetType.BLOG, blogId, reportId,
                    "폐쇄 예정 " + result.closeScheduledAt().format(AdminBlogService.SHOW_TIME));
        }
        return new Revocation(revocationId, cause, user(ownerId), user(result.newOwnerId()),
                result.closeScheduledAt(), canceled);
    }

    /** 대기 중 위임 요청을 자동 취소하고 받는 멤버에게 끌 수 없는 알림을 보낸다(D-103 #15). */
    private int cancelPendingDelegations(long blogId, String blogName, LocalDateTime now) {
        List<long[]> pending = jdbc.query("SELECT id, to_user_id FROM blog_delegations WHERE blog_id = ? "
                + "AND status = 'PENDING' FOR UPDATE", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, blogId);
        for (long[] d : pending) {
            jdbc.update("UPDATE blog_delegations SET status = 'AUTO_CANCELED', responded_at = ? WHERE id = ?", now, d[0]);
            notifications.write(d[1], NotificationType.DELEGATION_CANCELED, "BLOG", blogId, null,
                    "'" + blogName + "' 블로그장 위임 요청이 취소되었습니다.", "DELEGATION_CANCELED:" + d[0]);
        }
        return pending.size();
    }

    private UserRef user(Long id) {
        if (id == null) {
            return null;
        }
        return jdbc.query("SELECT nickname, status FROM users WHERE id = ?",
                rs -> rs.next() ? UserRef.of(id, rs.getString(1), rs.getString(2)) : null, id);
    }
}
