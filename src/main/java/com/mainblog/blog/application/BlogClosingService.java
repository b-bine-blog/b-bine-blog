package com.mainblog.blog.application;

import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.blog.domain.CloseReason;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 폐쇄 예약·철회·폐쇄 (T048, BLG-09, FR-072~FR-079, FR-082, FR-084, research R-13).
 * 폐쇄 예정 시각은 누른 날짜(한국 시간) + 7일의 04:00이다. 알림은 블로그장을 뺀 멤버와 구독자에게 시점마다 한 번만 간다.
 */
@Service
public class BlogClosingService {

    public static final String IMMEDIATE = "IMMEDIATE";
    public static final String WITHDRAWN = "WITHDRAWN";
    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final BlogRepository blogRepository;
    private final BlogPermissionService permissionService;
    private final JoinService joinService;
    private final NotificationWriter notifications;
    private final JdbcTemplate jdbc;
    private final BlogProperties properties;
    private final Clock clock;

    public BlogClosingService(BlogRepository blogRepository, BlogPermissionService permissionService,
                              JoinService joinService, NotificationWriter notifications, JdbcTemplate jdbc,
                              BlogProperties properties, Clock clock) {
        this.blogRepository = blogRepository;
        this.permissionService = permissionService;
        this.joinService = joinService;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /** 블로그장이 폐쇄 버튼을 누른다(FR-073). */
    @Transactional
    public LocalDateTime closeByOwner(long blogId, long userId) {
        Blog blog = lock(blogId);
        permissionService.require(blog, userId, BlogPermission.OWNER);
        return schedule(blog, CloseReason.OWNER, userId);
    }

    /**
     * 관리자 강제 폐쇄(FR-084)·블로그장 권한 박탈(FR-082)에서 부른다. 005의 관리자 API가 쓴다.
     * 철회는 FR-078대로 그 블로그의 블로그장만 할 수 있다.
     */
    @Transactional
    public LocalDateTime closeBySystem(long blogId, CloseReason reason, Long actorId) {
        Blog blog = lock(blogId);
        if (blog.isClosed()) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        return schedule(blog, reason, actorId);
    }

    /** 폐쇄 예정 시각: 지금 날짜 + grace-days의 hour시(FR-073). */
    public LocalDateTime scheduledTimeFor(LocalDateTime now) {
        return now.toLocalDate().plusDays(properties.closing().graceDays())
                .atTime(LocalTime.of(properties.closing().hour(), 0));
    }

    private LocalDateTime schedule(Blog blog, CloseReason reason, Long actorId) {
        if (blog.isClosing()) {
            throw new BusinessException(ErrorCode.BLOG_ALREADY_CLOSING);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime at = scheduledTimeFor(now);
        blog.scheduleClose(at, reason);
        blogRepository.flush();
        cancelPendingDelegations(blog.getId(), now);
        notifyAll(blog, at, IMMEDIATE, actorId, now);
        return at;
    }

    /** 블로그장만 철회한다(FR-078). 이후 알림·폐쇄는 일어나지 않는다. */
    @Transactional
    public void withdraw(long blogId, long userId) {
        Blog blog = lock(blogId);
        permissionService.require(blog, userId, BlogPermission.OWNER);
        if (!blog.isClosing()) {
            throw new BusinessException(ErrorCode.BLOG_NOT_CLOSING);
        }
        LocalDateTime at = blog.getCloseScheduledAt();
        blog.withdrawClose();
        blogRepository.flush();
        notifyAll(blog, at, WITHDRAWN, userId, LocalDateTime.now(clock));
    }

    /** 04:00 처리: 밀린 회차까지 D3·D1 알림을 보낸다. 보낸 알림 수를 돌려준다. */
    @Transactional
    public int sendDueNotices(LocalDateTime now) {
        int sent = 0;
        for (Integer days : properties.closing().noticeDays()) {
            List<Long> ids = jdbc.queryForList("SELECT id FROM blogs WHERE status = 'CLOSING' "
                    + "AND close_scheduled_at <= ?", Long.class, now.plusDays(days));
            for (Long id : ids) {
                Blog blog = lock(id);
                if (blog.isClosing()) {
                    sent += notifyAll(blog, blog.getCloseScheduledAt(), "D" + days, null, now);
                }
            }
        }
        return sent;
    }

    /** 폐쇄 예정 시각이 지난 블로그 번호. 04:00 처리가 블로그마다 따로 {@link #closeIfDue}를 부른다. */
    @Transactional(readOnly = true)
    public List<Long> dueBlogIds(LocalDateTime now) {
        return jdbc.queryForList("SELECT id FROM blogs WHERE status = 'CLOSING' AND close_scheduled_at <= ? "
                + "ORDER BY close_scheduled_at, id", Long.class, now);
    }

    /** 폐쇄하고 대기 중인 참여 신청·해제 문의를 자동 거절한다(FR-074, D-98 #15). 구독은 같은 트랜잭션에서 지운다(003 T025, D-102 #5). */
    @Transactional
    public boolean closeIfDue(long blogId, LocalDateTime now) {
        Blog blog = lock(blogId);
        if (!blog.isClosing() || blog.getCloseScheduledAt().isAfter(now)) {
            return false;
        }
        blog.close(now);
        blogRepository.flush();
        joinService.rejectAllPendingOnClose(blog, now);
        rejectPendingAppeals(blog, now);
        cancelPendingDelegations(blogId, now);
        jdbc.update("DELETE FROM blog_subscriptions WHERE blog_id = ?", blogId);
        return true;
    }

    private Blog lock(long blogId) {
        return blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
    }

    /** 폐쇄 버튼을 누르면 대기 중 위임 요청은 자동 취소한다(FR-072). 누른 사람이 요청한 블로그장이라 따로 알리지 않는다. */
    private void cancelPendingDelegations(long blogId, LocalDateTime now) {
        jdbc.update("UPDATE blog_delegations SET status = 'AUTO_CANCELED', responded_at = ? "
                + "WHERE blog_id = ? AND status = 'PENDING'", now, blogId);
    }

    private void rejectPendingAppeals(Blog blog, LocalDateTime now) {
        List<Long[]> pending = jdbc.query("""
                SELECT a.id, a.user_id FROM blacklist_appeals a JOIN blog_blacklists b ON b.id = a.blacklist_id
                WHERE b.blog_id = ? AND a.status = 'PENDING'""",
                (rs, i) -> new Long[] {rs.getLong(1), rs.getLong(2)}, blog.getId());
        for (Long[] row : pending) {
            jdbc.update("UPDATE blacklist_appeals SET status = 'REJECTED', processed_at = ? WHERE id = ?", now, row[0]);
            notifications.write(row[1], NotificationType.APPEAL_REJECTED, "BLOG", blog.getId(), null,
                    "'" + blog.getName() + "' 블로그가 폐쇄되어 해제 문의가 거절되었습니다.", "APPEAL_CLOSED:" + row[0]);
        }
    }

    /**
     * 블로그장을 뺀 멤버 전원과 구독자 전원(중복 제외, 탈퇴 회원 제외)에게 보낸다. 대상은 보낼 때마다 다시 계산한다.
     * blog_closing_notices와 event_key를 INSERT IGNORE로 넣어 다시 실행해도 한 사람에게 한 번만 간다(FR-077).
     */
    private int notifyAll(Blog blog, LocalDateTime at, String stage, Long actorId, LocalDateTime now) {
        List<Long> recipients = jdbc.queryForList("""
                SELECT u.id FROM users u
                WHERE u.status = 'ACTIVE'
                  AND u.id IN (SELECT m.user_id FROM blog_members m WHERE m.blog_id = ? AND m.role <> 'OWNER'
                               UNION SELECT s.user_id FROM blog_subscriptions s WHERE s.blog_id = ?)
                  AND u.id NOT IN (SELECT o.user_id FROM blog_members o WHERE o.blog_id = ? AND o.role = 'OWNER')
                ORDER BY u.id""", Long.class, blog.getId(), blog.getId(), blog.getId());
        NotificationType type = WITHDRAWN.equals(stage) ? NotificationType.BLOG_CLOSING_WITHDRAWN
                : NotificationType.BLOG_CLOSING;
        String content = message(blog, at, stage);
        String eventKey = "BLOG_CLOSING:" + blog.getId() + ":" + at.format(KEY_TIME) + ":" + stage;
        int sent = 0;
        for (Long userId : recipients) {
            int inserted = jdbc.update("INSERT IGNORE INTO blog_closing_notices "
                    + "(blog_id, user_id, close_scheduled_at, stage, sent_at) VALUES (?, ?, ?, ?, ?)",
                    blog.getId(), userId, at, stage, now);
            if (inserted > 0 && notifications.write(userId, type, "BLOG", blog.getId(), actorId, content, eventKey)) {
                sent++;
            }
        }
        return sent;
    }

    private static String message(Blog blog, LocalDateTime at, String stage) {
        String name = "'" + blog.getName() + "' 블로그";
        if (WITHDRAWN.equals(stage)) {
            return name + " 폐쇄가 철회되었습니다.";
        }
        String text = name + "가 " + at.format(SHOW_TIME) + "에 폐쇄됩니다.";
        if (blog.getCloseReason() == CloseReason.OWNER_REVOKED) {
            text += " 블로그장 권한 박탈로 인한 폐쇄 조치입니다.";
        } else if (blog.getCloseReason() == CloseReason.ADMIN) {
            text += " 운영 정책에 따른 폐쇄 조치입니다.";
        }
        return text;
    }
}
