package com.mainblog.admin.application;

import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.admin.application.AdminViews.UserRef;
import com.mainblog.blog.application.BlogClosingService;
import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.blog.domain.CloseReason;
import com.mainblog.common.config.AdminProperties;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 블로그 조회·숨김·강제 폐쇄·강제 폐쇄 철회 (005 T023, ADM-02, FR-011~FR-014, D-94, D-103 #6~#8, D-104,
 * research R-05·R-07·R-08).
 * 블로그 상태를 바꾸는 일은 002 서비스(BlogClosingService)를 부른다. 조회는 여러 테이블을 묶어 JdbcTemplate으로 한다.
 */
@Service
public class AdminBlogService {

    public record BlogSummary(long id, String name, String address, String visibility, String status, boolean hidden,
                              LocalDateTime closeScheduledAt, String closeReason, UserRef owner, int memberCount,
                              int postCount, LocalDateTime createdAt) {}

    public record Manager(UserRef user, LocalDateTime managerSince) {}

    public record BlogDetail(long id, String name, String address, String visibility, String status, boolean hidden,
                             LocalDateTime closeScheduledAt, String closeReason, UserRef owner, int memberCount,
                             int postCount, LocalDateTime createdAt, int ownerActiveWarningCount,
                             List<Manager> managers, boolean pendingDelegation, LocalDateTime hiddenAt) {}

    public record CloseResult(LocalDateTime closeScheduledAt, boolean keptExisting) {}

    static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 강제 폐쇄 철회 기록의 memo(RESTORE·BLOG, D-104). */
    static final String WITHDRAW_MEMO = "강제 폐쇄 철회";

    private static final String SELECT = """
            SELECT b.id, b.name, b.address, b.visibility, b.status, b.hidden, b.hidden_at, b.close_scheduled_at,
                   b.close_reason, b.member_count, b.created_at, om.user_id AS owner_id, ou.nickname AS owner_nickname,
                   ou.status AS owner_status,
                   (SELECT COUNT(*) FROM posts p WHERE p.blog_id = b.id AND p.board_type = 'BLOG'
                      AND p.status <> 'DELETED') AS post_count
            FROM blogs b
            LEFT JOIN blog_members om ON om.blog_id = b.id AND om.role = 'OWNER'
            LEFT JOIN users ou ON ou.id = om.user_id
            """;

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final AdminActionLog actionLog;
    private final AdminPaging paging;
    private final BlogRepository blogRepository;
    private final BlogClosingService closingService;
    private final NotificationWriter notifications;
    private final AdminProperties props;
    private final BlogProperties blogProps;
    private final Clock clock;

    public AdminBlogService(JdbcTemplate jdbc, AdminGuard guard, AdminActionLog actionLog, AdminPaging paging,
                            BlogRepository blogRepository, BlogClosingService closingService,
                            NotificationWriter notifications, AdminProperties props, BlogProperties blogProps,
                            Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.actionLog = actionLog;
        this.paging = paging;
        this.blogRepository = blogRepository;
        this.closingService = closingService;
        this.notifications = notifications;
        this.props = props;
        this.blogProps = blogProps;
        this.clock = clock;
    }

    /**
     * 이름·주소·블로그장 닉네임 일부 일치(비우면 전체). 공개 범위와 관계없이 운영 중·폐쇄 예정·폐쇄됨(30일 정리 전)·숨김을 모두 보여 준다.
     * status: ACTIVE·CLOSING·CLOSED, HIDDEN은 숨긴 블로그만. 최근 생성 순(D-103 #6).
     */
    @Transactional(readOnly = true)
    public Page<BlogSummary> search(long adminId, String q, String status, Integer page, Integer size) {
        guard.require(adminId);
        int p = paging.page(page);
        int s = paging.size(size);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("(b.status <> 'CLOSED' OR b.closed_at > ?)");
        args.add(LocalDateTime.now(clock).minus(blogProps.closing().purgeAfter()));
        String keyword = q == null ? "" : q.strip();
        if (!keyword.isEmpty()) {
            String like = "%" + AdminMemberService.likeEscape(keyword) + "%";
            where.append(" AND (b.name LIKE ? OR b.address LIKE ? OR ou.nickname LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            switch (status) {
                case "ACTIVE", "CLOSING", "CLOSED" -> {
                    where.append(" AND b.status = ?");
                    args.add(status);
                }
                case "HIDDEN" -> where.append(" AND b.hidden = TRUE");
                default -> throw new InvalidInputException("status", "Pattern");
            }
        }
        String from = " FROM blogs b LEFT JOIN blog_members om ON om.blog_id = b.id AND om.role = 'OWNER' "
                + "LEFT JOIN users ou ON ou.id = om.user_id WHERE " + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add(AdminPaging.offset(p, s));
        List<BlogSummary> items = jdbc.query(SELECT + "WHERE " + where
                + " ORDER BY b.created_at DESC, b.id DESC LIMIT ? OFFSET ?", AdminBlogService::summary,
                pageArgs.toArray());
        return Page.of(items, p, s, total == null ? 0 : total);
    }

    /** 상세: 지금 블로그장의 1년 안 경고 수, 부블로그장 목록(된 순), 대기 중 위임 요청 여부. */
    @Transactional(readOnly = true)
    public BlogDetail detail(long adminId, long blogId) {
        guard.require(adminId);
        List<BlogDetail> rows = jdbc.query(SELECT + "WHERE b.id = ?", (rs, i) -> {
            BlogSummary s = summary(rs, i);
            return new BlogDetail(s.id(), s.name(), s.address(), s.visibility(), s.status(), s.hidden(),
                    s.closeScheduledAt(), s.closeReason(), s.owner(), s.memberCount(), s.postCount(), s.createdAt(),
                    0, List.of(), false, rs.getObject("hidden_at", LocalDateTime.class));
        }, blogId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        BlogDetail d = rows.get(0);
        int warnings = d.owner() == null ? 0 : activeWarningCount(blogId, d.owner().id());
        List<Manager> managers = jdbc.query("""
                SELECT m.user_id, u.nickname, u.status, m.manager_since FROM blog_members m JOIN users u ON u.id = m.user_id
                WHERE m.blog_id = ? AND m.role = 'MANAGER' ORDER BY m.manager_since, m.id""",
                (rs, i) -> new Manager(UserRef.of(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("status")),
                        rs.getObject("manager_since", LocalDateTime.class)), blogId);
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM blog_delegations WHERE blog_id = ? "
                + "AND status = 'PENDING'", Integer.class, blogId);
        return new BlogDetail(d.id(), d.name(), d.address(), d.visibility(), d.status(), d.hidden(),
                d.closeScheduledAt(), d.closeReason(), d.owner(), d.memberCount(), d.postCount(), d.createdAt(),
                warnings, managers, pending != null && pending > 0, d.hiddenAt());
    }

    /** 숨김: 블로그장에게만 끌 수 없는 BLOG_HIDDEN 알림, BLOG_HIDE 기록(FR-012, D-103 #7). 폐쇄 예정 블로그도 숨길 수 있다. */
    @Transactional
    public void hide(long adminId, long blogId) {
        guard.require(adminId);
        Blog blog = lockOpen(blogId);
        if (blog.isHidden()) {
            throw new BusinessException(ErrorCode.ALREADY_HIDDEN);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        blog.hide(now);
        blogRepository.flush();
        Long ownerId = currentOwner(blogId);
        if (ownerId != null) {
            notifications.write(ownerId, NotificationType.BLOG_HIDDEN, "BLOG", blogId, null,
                    "'" + blog.getName() + "' 블로그가 운영 정책에 따라 숨김 처리되었습니다. 메인 목록·검색·피드에 나오지 않고 "
                            + "멤버만 볼 수 있습니다.", "BLOG_HIDDEN:" + blogId + ":" + now.format(KEY_TIME));
        }
        actionLog.write(adminId, ActionType.BLOG_HIDE, TargetType.BLOG, blogId, null, null);
    }

    /** 숨김 해제: BLOG_UNHIDE 기록, 알림 없음(research 가정 4). */
    @Transactional
    public void unhide(long adminId, long blogId) {
        guard.require(adminId);
        Blog blog = lockOpen(blogId);
        if (!blog.isHidden()) {
            throw new BusinessException(ErrorCode.NOT_HIDDEN);
        }
        blog.unhide();
        blogRepository.flush();
        actionLog.write(adminId, ActionType.BLOG_UNHIDE, TargetType.BLOG, blogId, null, null);
    }

    @Transactional
    public CloseResult forceClose(long adminId, long blogId) {
        guard.require(adminId);
        return forceClose(adminId, blogId, null);
    }

    /**
     * 강제 폐쇄(FR-013, D-94). 운영 중이면 누른 날짜 + 7일 04:00 예약과 즉시 알림(002 closeBySystem).
     * 이미 폐쇄 예정이면 시각·알림은 그대로 두고 사유만 ADMIN으로 바꾼다(D-103 #8). 신고 처리에서도 부른다(reportId).
     * 호출하는 쪽이 같은 트랜잭션에서 관리자 확인을 마쳤다.
     */
    @Transactional
    public CloseResult forceClose(long adminId, long blogId, Long reportId) {
        Blog blog = lockOpen(blogId);
        boolean kept = blog.isClosing();
        LocalDateTime at = kept ? closingService.markAdminClose(blogId)
                : closingService.closeBySystem(blogId, CloseReason.ADMIN, adminId);
        actionLog.write(adminId, ActionType.BLOG_FORCE_CLOSE, TargetType.BLOG, blogId, reportId,
                "폐쇄 예정 " + at.format(SHOW_TIME) + (kept ? " (기존 폐쇄 예정 유지)" : ""));
        return new CloseResult(at, kept);
    }

    /**
     * 강제 폐쇄 철회(ADM-02, BLG-09, D-104). 관리자가 폐쇄한(close_reason = ADMIN) 폐쇄 예정 블로그만 철회한다.
     * 운영 중으로 돌리고 블로그장 철회와 같은 철회 알림을 보낸다. 활동 기록은 RESTORE·BLOG로 남긴다
     * (action_type 값을 늘리지 않으려고 기존 RESTORE를 쓴다).
     */
    @Transactional
    public void withdrawForceClose(long adminId, long blogId) {
        guard.require(adminId);
        lockOpen(blogId);
        closingService.withdrawByAdmin(blogId, adminId);
        actionLog.write(adminId, ActionType.RESTORE, TargetType.BLOG, blogId, null, WITHDRAW_MEMO);
    }

    /** 블로그 행을 잠근다. 없으면 404, 폐쇄됐으면 409 BLOG_ALREADY_CLOSED. */
    Blog lockOpen(long blogId) {
        Blog blog = blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        if (blog.isClosed()) {
            throw new BusinessException(ErrorCode.BLOG_ALREADY_CLOSED);
        }
        return blog;
    }

    /** 지금 블로그장. 블로그 행을 잠근 뒤 잠금 읽기로 최신 값을 본다(동시 경고·강퇴, SC-005). 없으면 null. */
    Long currentOwner(long blogId) {
        List<Long> ids = jdbc.queryForList("SELECT user_id FROM blog_members WHERE blog_id = ? AND role = 'OWNER' "
                + "FOR SHARE", Long.class, blogId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 그 블로그·그 회원의 1년 안 경고 수(D-95, D-103 #13). 잠금 읽기로 동시에 들어온 경고까지 센다. */
    int activeWarningCount(long blogId, long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM owner_warnings WHERE blog_id = ? AND user_id = ? "
                + "AND created_at > ? FOR SHARE", Integer.class, blogId, userId,
                LocalDateTime.now(clock).minus(props.warning().window()));
        return n == null ? 0 : n;
    }

    private static BlogSummary summary(ResultSet rs, int i) throws SQLException {
        Long ownerId = rs.getObject("owner_id", Long.class);
        return new BlogSummary(rs.getLong("id"), rs.getString("name"), rs.getString("address"),
                rs.getString("visibility"), rs.getString("status"), rs.getBoolean("hidden"),
                rs.getObject("close_scheduled_at", LocalDateTime.class), rs.getString("close_reason"),
                UserRef.of(ownerId, rs.getString("owner_nickname"), rs.getString("owner_status")),
                rs.getInt("member_count"), rs.getInt("post_count"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
