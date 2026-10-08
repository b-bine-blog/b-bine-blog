package com.mainblog.admin.application;

import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.admin.application.AdminViews.UserRef;
import com.mainblog.board.application.PostService;
import com.mainblog.board.application.PostStore;
import com.mainblog.board.application.PostStore.PostRow;
import com.mainblog.common.config.BoardProperties;
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
 * 관리자 글 보기·숨김·해제·삭제·복구 (005 T039, ADM-03, FR-015~FR-019, 4.5, D-58, D-103 #9~#11, research R-11).
 * 블로그 글만 다룬다(공지는 004 화면). 숨김은 기간 없이 유지하고, 삭제만 30일 뒤 04:00 정리에서 지운다(004 BoardDailyTasks).
 * 004의 DELETE /api/posts/{id}를 관리자가 불러도 이 서비스가 지워 활동 기록이 빠지지 않는다(PostService.AdminPostDelete).
 */
@Service
public class AdminPostModerationService implements PostService.AdminPostDelete {

    static final List<String> STATUSES = List.of("VISIBLE", "HIDDEN", "DELETED");
    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public record BlogRef(long id, String name, String status) {}

    public record AdminPostSummary(long id, BlogRef blog, String title, UserRef author, String status,
                                   String deletedBy, LocalDateTime deletedAt, boolean restorable,
                                   LocalDateTime createdAt) {}

    public record AdminPostDetail(long id, BlogRef blog, String title, UserRef author, String status,
                                  String deletedBy, LocalDateTime deletedAt, boolean restorable,
                                  LocalDateTime createdAt, String content, LocalDateTime hiddenAt) {}

    private static final String SELECT = """
            SELECT p.id, p.blog_id, b.name AS blog_name, b.status AS blog_status, p.title, p.content, p.status,
                   p.author_id, u.nickname AS author_nickname, u.status AS author_status, p.deleted_by,
                   du.role AS deleter_role, p.deleted_at, p.hidden_at, p.created_at
            FROM posts p JOIN users u ON u.id = p.author_id LEFT JOIN blogs b ON b.id = p.blog_id
            LEFT JOIN users du ON du.id = p.deleted_by
            """;

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final AdminActionLog actionLog;
    private final AdminPaging paging;
    private final PostStore store;
    private final PostService postService;
    private final NotificationWriter notifications;
    private final BoardProperties boardProps;
    private final Clock clock;

    public AdminPostModerationService(JdbcTemplate jdbc, AdminGuard guard, AdminActionLog actionLog,
                                      AdminPaging paging, PostStore store, PostService postService,
                                      NotificationWriter notifications, BoardProperties boardProps, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.actionLog = actionLog;
        this.paging = paging;
        this.store = store;
        this.postService = postService;
        this.notifications = notifications;
        this.boardProps = boardProps;
        this.clock = clock;
    }

    /** 공개 범위·블로그 숨김과 관계없이. 삭제 글은 30일 안의 것만. 최근 쓴 순. */
    @Transactional(readOnly = true)
    public Page<AdminPostSummary> list(long adminId, Long blogId, String q, String status, Integer page, Integer size) {
        guard.require(adminId);
        int p = paging.page(page);
        int n = paging.size(size);
        StringBuilder where = new StringBuilder(" WHERE p.board_type = 'BLOG' AND (p.status <> 'DELETED' "
                + "OR p.deleted_at > ?)");
        List<Object> args = new ArrayList<>(List.of(cutoff()));
        if (status != null && !status.isBlank()) {
            if (!STATUSES.contains(status)) {
                throw new InvalidInputException("status", "Pattern");
            }
            where.append(" AND p.status = ?");
            args.add(status);
        }
        if (blogId != null) {
            where.append(" AND p.blog_id = ?");
            args.add(blogId);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND p.title LIKE ?");
            args.add("%" + AdminMemberService.likeEscape(q.strip()) + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM posts p" + where, Long.class, args.toArray());
        args.add(n);
        args.add(AdminPaging.offset(p, n));
        List<AdminPostSummary> items = jdbc.query(SELECT + where + " ORDER BY p.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> summary(rs), args.toArray());
        return Page.of(items, p, n, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public AdminPostDetail detail(long adminId, long postId) {
        guard.require(adminId);
        List<AdminPostDetail> rows = jdbc.query(SELECT + " WHERE p.id = ? AND p.board_type = 'BLOG'", (rs, i) -> {
            AdminPostSummary s = summary(rs);
            return new AdminPostDetail(s.id(), s.blog(), s.title(), s.author(), s.status(), s.deletedBy(),
                    s.deletedAt(), s.restorable(), s.createdAt(), rs.getString("content"),
                    rs.getObject("hidden_at", LocalDateTime.class));
        }, postId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return rows.get(0);
    }

    /** 숨김: 작성자에게 끌 수 없는 "내 글 숨겨짐"(D-103 #9). 숨김·삭제 상태면 409 ALREADY_HIDDEN. */
    @Transactional
    public void hide(long adminId, long postId) {
        guard.require(adminId);
        PostRow post = lock(postId);
        if (!post.isVisible()) {
            throw new BusinessException(ErrorCode.ALREADY_HIDDEN);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("UPDATE posts SET status = 'HIDDEN', hidden_at = ?, updated_at = ? WHERE id = ?", now, now, postId);
        notifications.write(post.authorId(), NotificationType.POST_HIDDEN, "POST", postId, null,
                "'" + post.title() + "' 글을 관리자가 숨겼습니다. 이 글은 나에게만 보입니다.",
                "POST_HIDDEN:" + postId + ":" + now.format(KEY_TIME));
        actionLog.write(adminId, ActionType.POST_HIDE, TargetType.POST, postId, null, null);
    }

    /** 숨김 해제: 알림 없음(D-103 #10). */
    @Transactional
    public void unhide(long adminId, long postId) {
        guard.require(adminId);
        PostRow post = lock(postId);
        if (!post.isHiddenByAdmin()) {
            throw new BusinessException(ErrorCode.NOT_HIDDEN);
        }
        jdbc.update("UPDATE posts SET status = 'VISIBLE', hidden_at = NULL, updated_at = ? WHERE id = ?",
                LocalDateTime.now(clock), postId);
        actionLog.write(adminId, ActionType.POST_UNHIDE, TargetType.POST, postId, null, null);
    }

    /** 삭제: VISIBLE·HIDDEN → DELETED(30일 보관). hidden_at은 남겨 복구하면 숨김으로 돌아간다. */
    @Override
    @Transactional
    public void delete(long adminId, long postId) {
        guard.require(adminId);
        PostRow post = lock(postId);
        if (!post.isVisible() && !post.isHiddenByAdmin()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.markDeleted(postId, adminId, now);
        postService.notifyDeleted(post, adminId, now);
        actionLog.write(adminId, ActionType.POST_DELETE, TargetType.POST, postId, null, null);
    }

    /**
     * 복구(FR-019, D-103 #11): 30일 안에 관리자·블로그장(글 관리 권한자)이 지운 글만. 숨긴 채 지운 글은 숨김으로 돌아간다.
     * 사진은 글이 30일 정리될 때만 지워지므로 그대로 다시 보인다. 작성자에게 "운영" 탭 알림.
     */
    @Transactional
    public void restore(long adminId, long postId) {
        guard.require(adminId);
        lock(postId);
        AdminPostDetail d = detail(adminId, postId);
        if (!"DELETED".equals(d.status()) || !restorableBy(d.deletedBy())) {
            throw new BusinessException(ErrorCode.NOT_RESTORABLE);
        }
        if (d.deletedAt() != null && !d.deletedAt().isAfter(cutoff())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (d.blog() == null || "CLOSED".equals(d.blog().status())) {
            throw new BusinessException(ErrorCode.BLOG_ALREADY_CLOSED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("UPDATE posts SET status = ?, deleted_at = NULL, deleted_by = NULL, updated_at = ? WHERE id = ?",
                d.hiddenAt() != null ? "HIDDEN" : "VISIBLE", now, postId);
        notifications.write(d.author().id(), NotificationType.POST_RESTORED, "POST", postId, null,
                "'" + d.title() + "' 글이 복구되었습니다.", "POST_RESTORED:" + postId + ":" + now.format(KEY_TIME));
        actionLog.write(adminId, ActionType.RESTORE, TargetType.POST, postId, null, null);
    }

    private PostRow lock(long postId) {
        PostRow post = store.findForUpdate(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (post.isNotice()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private LocalDateTime cutoff() {
        return LocalDateTime.now(clock).minus(boardProps.purgeAfter());
    }

    /** AUTHOR·BLOG_MANAGER·ADMIN. 지운 상태가 아니면 null. */
    static String deletedBy(String status, Long deletedBy, long authorId, String deleterRole) {
        if (!"DELETED".equals(status) || deletedBy == null) {
            return null;
        }
        if (deletedBy == authorId) {
            return "AUTHOR";
        }
        return "ADMIN".equals(deleterRole) ? "ADMIN" : "BLOG_MANAGER";
    }

    static boolean restorableBy(String deletedBy) {
        return "ADMIN".equals(deletedBy) || "BLOG_MANAGER".equals(deletedBy);
    }

    private AdminPostSummary summary(ResultSet rs) throws SQLException {
        Long blogId = rs.getObject("blog_id", Long.class);
        String blogStatus = rs.getString("blog_status");
        BlogRef blog = blogId == null ? null : new BlogRef(blogId, rs.getString("blog_name"), blogStatus);
        long authorId = rs.getLong("author_id");
        String status = rs.getString("status");
        String deletedBy = deletedBy(status, rs.getObject("deleted_by", Long.class), authorId,
                rs.getString("deleter_role"));
        LocalDateTime deletedAt = rs.getObject("deleted_at", LocalDateTime.class);
        boolean restorable = restorableBy(deletedBy) && blogStatus != null && !"CLOSED".equals(blogStatus)
                && deletedAt != null && deletedAt.isAfter(cutoff());
        return new AdminPostSummary(rs.getLong("id"), blog, rs.getString("title"),
                UserRef.of(authorId, rs.getString("author_nickname"), rs.getString("author_status")), status,
                deletedBy, deletedAt, restorable, rs.getObject("created_at", LocalDateTime.class));
    }
}
