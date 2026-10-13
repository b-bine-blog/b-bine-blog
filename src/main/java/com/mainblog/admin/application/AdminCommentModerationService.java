package com.mainblog.admin.application;

import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.admin.application.AdminViews.UserRef;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 댓글 보기·숨김·해제·삭제·복구 (005 T040, ADM-03, FR-015, FR-019, D-103 #9·#11, research R-11).
 * 작성자에게 알리지 않는다(D-103 #9). 누가 지웠는지는 comments.deleted_by로 판단한다(1단계에서 칸이 생김, T057).
 */
@Service
public class AdminCommentModerationService {

    public record AdminComment(long id, long postId, Long parentId, UserRef author, String content, String status,
                               String deletedBy, LocalDateTime deletedAt, boolean restorable,
                               LocalDateTime createdAt) {}

    private record Row(long id, long postId, String status, Long deletedBy, long authorId, String deleterRole,
                       LocalDateTime deletedAt, String blogStatus) {}

    private static final String SELECT = """
            SELECT c.id, c.post_id, c.parent_id, c.author_id, u.nickname AS author_nickname, u.status AS author_status,
                   c.content, c.status, c.deleted_by, du.role AS deleter_role, c.deleted_at, c.created_at,
                   b.status AS blog_status
            FROM comments c JOIN users u ON u.id = c.author_id LEFT JOIN users du ON du.id = c.deleted_by
            JOIN posts p ON p.id = c.post_id LEFT JOIN blogs b ON b.id = p.blog_id
            """;

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final AdminActionLog actionLog;
    private final AdminPaging paging;
    private final BoardProperties boardProps;
    private final Clock clock;

    public AdminCommentModerationService(JdbcTemplate jdbc, AdminGuard guard, AdminActionLog actionLog,
                                         AdminPaging paging, BoardProperties boardProps, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.actionLog = actionLog;
        this.paging = paging;
        this.boardProps = boardProps;
        this.clock = clock;
    }

    /** 글 번호가 있으면 그 글의 댓글을 쓴 순으로, 없으면 전체에서 최근 순. 삭제는 30일 안의 것만. */
    @Transactional(readOnly = true)
    public Page<AdminComment> list(long adminId, Long postId, String status, Integer page, Integer size) {
        guard.require(adminId);
        int p = paging.page(page);
        int n = paging.size(size);
        StringBuilder where = new StringBuilder(" WHERE (c.status <> 'DELETED' OR c.deleted_at > ?)");
        List<Object> args = new ArrayList<>(List.of(cutoff()));
        if (status != null && !status.isBlank()) {
            if (!AdminPostModerationService.STATUSES.contains(status)) {
                throw new InvalidInputException("status", "Pattern");
            }
            where.append(" AND c.status = ?");
            args.add(status);
        }
        if (postId != null) {
            where.append(" AND c.post_id = ?");
            args.add(postId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM comments c" + where, Long.class, args.toArray());
        args.add(n);
        args.add(AdminPaging.offset(p, n));
        List<AdminComment> items = jdbc.query(SELECT + where + " ORDER BY c.id " + (postId != null ? "ASC" : "DESC")
                + " LIMIT ? OFFSET ?", (rs, i) -> {
            long authorId = rs.getLong("author_id");
            String st = rs.getString("status");
            String by = AdminPostModerationService.deletedBy(st, rs.getObject("deleted_by", Long.class), authorId,
                    rs.getString("deleter_role"));
            LocalDateTime deletedAt = rs.getObject("deleted_at", LocalDateTime.class);
            String blogStatus = rs.getString("blog_status");
            boolean restorable = AdminPostModerationService.restorableBy(by) && blogStatus != null
                    && !"CLOSED".equals(blogStatus) && deletedAt != null && deletedAt.isAfter(cutoff());
            return new AdminComment(rs.getLong("id"), rs.getLong("post_id"), rs.getObject("parent_id", Long.class),
                    UserRef.of(authorId, rs.getString("author_nickname"), rs.getString("author_status")),
                    rs.getString("content"), st, by, deletedAt, restorable,
                    rs.getObject("created_at", LocalDateTime.class));
        }, args.toArray());
        return Page.of(items, p, n, total == null ? 0 : total);
    }

    @Transactional
    public void hide(long adminId, long commentId) {
        guard.require(adminId);
        Row c = lock(commentId);
        if (!"VISIBLE".equals(c.status())) {
            throw new BusinessException(ErrorCode.ALREADY_HIDDEN);
        }
        jdbc.update("UPDATE comments SET status = 'HIDDEN', updated_at = ? WHERE id = ?", LocalDateTime.now(clock),
                commentId);
        actionLog.write(adminId, ActionType.COMMENT_HIDE, TargetType.COMMENT, commentId, null, null);
    }

    @Transactional
    public void unhide(long adminId, long commentId) {
        guard.require(adminId);
        Row c = lock(commentId);
        if (!"HIDDEN".equals(c.status())) {
            throw new BusinessException(ErrorCode.NOT_HIDDEN);
        }
        jdbc.update("UPDATE comments SET status = 'VISIBLE', updated_at = ? WHERE id = ?", LocalDateTime.now(clock),
                commentId);
        actionLog.write(adminId, ActionType.COMMENT_UNHIDE, TargetType.COMMENT, commentId, null, null);
    }

    /** VISIBLE·HIDDEN → DELETED(30일 보관). 이미 지운 댓글은 404. */
    @Transactional
    public void delete(long adminId, long commentId) {
        guard.require(adminId);
        Row c = lock(commentId);
        if ("DELETED".equals(c.status())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("UPDATE comments SET status = 'DELETED', deleted_at = ?, deleted_by = ?, updated_at = ? WHERE id = ?",
                now, adminId, now, commentId);
        actionLog.write(adminId, ActionType.COMMENT_DELETE, TargetType.COMMENT, commentId, null, null);
    }

    /**
     * 30일 안에 관리자·블로그장이 지운 댓글만 보임으로 돌린다(D-103 #11). 작성자가 지운 것은 409 NOT_RESTORABLE.
     * 댓글은 숨김 시각 칸이 없어 숨긴 뒤 지운 댓글도 보임으로 돌아간다(research R-11 가정).
     */
    @Transactional
    public void restore(long adminId, long commentId) {
        guard.require(adminId);
        Row c = lock(commentId);
        String by = AdminPostModerationService.deletedBy(c.status(), c.deletedBy(), c.authorId(), c.deleterRole());
        if (!AdminPostModerationService.restorableBy(by)) {
            throw new BusinessException(ErrorCode.NOT_RESTORABLE);
        }
        if (c.deletedAt() != null && !c.deletedAt().isAfter(cutoff())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (c.blogStatus() == null || "CLOSED".equals(c.blogStatus())) {
            throw new BusinessException(ErrorCode.BLOG_ALREADY_CLOSED);
        }
        jdbc.update("UPDATE comments SET status = 'VISIBLE', deleted_at = NULL, deleted_by = NULL, updated_at = ? "
                + "WHERE id = ?", LocalDateTime.now(clock), commentId);
        actionLog.write(adminId, ActionType.RESTORE, TargetType.COMMENT, commentId, null, null);
    }

    private Row lock(long commentId) {
        List<Row> rows = jdbc.query("""
                SELECT c.id, c.post_id, c.status, c.deleted_by, c.author_id, du.role AS deleter_role, c.deleted_at,
                       b.status AS blog_status
                FROM comments c LEFT JOIN users du ON du.id = c.deleted_by JOIN posts p ON p.id = c.post_id
                LEFT JOIN blogs b ON b.id = p.blog_id WHERE c.id = ? FOR UPDATE OF c""",
                (rs, i) -> new Row(rs.getLong("id"), rs.getLong("post_id"), rs.getString("status"),
                        rs.getObject("deleted_by", Long.class), rs.getLong("author_id"), rs.getString("deleter_role"),
                        rs.getObject("deleted_at", LocalDateTime.class), rs.getString("blog_status")), commentId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return rows.get(0);
    }

    private LocalDateTime cutoff() {
        return LocalDateTime.now(clock).minus(boardProps.purgeAfter());
    }
}
