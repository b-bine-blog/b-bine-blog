package com.mainblog.board.application;

import com.mainblog.board.application.BoardViews.CommentPage;
import com.mainblog.board.application.BoardViews.CommentView;
import com.mainblog.board.application.PostPolicy.Viewer;
import com.mainblog.board.application.PostStore.PostRow;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글·답글 (004 T037, FR-036~FR-041, FR-044, FR-045, D-87, D-100 #1~#3·#9~#11, research R-08).
 * 답글은 1단계: 답글에 답하면 같은 부모 아래에 달고 replyTo는 그 답글의 작성자다.
 * 답글이 남은 최상위 댓글을 지우면 "삭제된 댓글입니다" 자리로 남고, 보이는 답글이 없어지면 목록에서 빠진다.
 */
@Service
public class CommentService {

    public record CommentWriteRequest(String content, Long parentId) {}

    public record CommentEditRequest(String content) {}

    private record Row(long id, Long parentId, long postId, long authorId, Long replyToUserId, String content,
                       boolean edited, String status, LocalDateTime createdAt, String nickname, String userStatus,
                       String profileName, String replyToNickname, String replyToStatus) {}

    private static final String SELECT = "SELECT cm.id, cm.parent_id, cm.post_id, cm.author_id, cm.reply_to_user_id, "
            + "cm.content, cm.edited, cm.status, cm.created_at, u.nickname, u.status AS user_status, "
            + AuthorView.PROFILE_NAME_SQL + " AS profile_name, ru.nickname AS reply_nickname, ru.status AS reply_status "
            + "FROM comments cm JOIN users u ON u.id = cm.author_id LEFT JOIN users ru ON ru.id = cm.reply_to_user_id ";

    /** 보는 회원이 차단한 작성자의 댓글을 뺀다. 둘 다 그 블로그 멤버면 넣는다(FR-045). 파라미터: 보는 회원, 블로그 번호 2번. */
    private static final String NOT_BLOCKED = " AND NOT EXISTS (SELECT 1 FROM user_blocks ub WHERE ub.blocker_id = :viewer "
            + "AND ub.blocked_id = cm.author_id AND NOT ("
            + "EXISTS (SELECT 1 FROM blog_members m1 WHERE m1.blog_id = :blog AND m1.user_id = ub.blocker_id) "
            + "AND EXISTS (SELECT 1 FROM blog_members m2 WHERE m2.blog_id = :blog AND m2.user_id = ub.blocked_id)))";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final PostStore store;
    private final PostPolicy policy;
    private final AuthorView authorView;
    private final NotificationWriter notifications;
    private final BoardPaging paging;
    private final BoardProperties props;
    private final Clock clock;

    public CommentService(JdbcTemplate jdbc, PostStore store, PostPolicy policy, AuthorView authorView,
                          NotificationWriter notifications, BoardPaging paging, BoardProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.store = store;
        this.policy = policy;
        this.authorView = authorView;
        this.notifications = notifications;
        this.paging = paging;
        this.props = props;
        this.clock = clock;
    }

    /** 최상위 댓글은 작성순 20개씩, 각 답글은 모두 함께. */
    @Transactional(readOnly = true)
    public CommentPage list(long postId, Long userId, Integer page) {
        int p = paging.page(page);
        int size = props.comment().pageSize();
        PostRow post = findPost(postId);
        Viewer viewer = policy.requireReadable(post, userId);
        if (post.isNotice()) {
            return new CommentPage(List.of(), p, 0, 0);
        }
        MapSqlParameterSource params = new MapSqlParameterSource("post", postId).addValue("blog", post.blogId())
                .addValue("viewer", userId);
        String block = userId == null ? "" : NOT_BLOCKED;
        // 지우거나 관리자가 숨긴 최상위 댓글은 보이는 답글이 남았을 때만 자리로 둔다(005 T042)
        String topWhere = "WHERE cm.post_id = :post AND cm.parent_id IS NULL AND (cm.status = 'VISIBLE' "
                + "OR (cm.status IN ('DELETED', 'HIDDEN') AND EXISTS (SELECT 1 FROM comments r WHERE r.parent_id = cm.id "
                + "AND r.status = 'VISIBLE')))" + block;
        Long total = named.queryForObject("SELECT COUNT(*) FROM comments cm " + topWhere, params, Long.class);
        params.addValue("limit", size).addValue("offset", (p - 1) * size);
        List<Row> tops = named.query(SELECT + topWhere + " ORDER BY cm.id LIMIT :limit OFFSET :offset", params,
                CommentService::map);
        Map<Long, List<CommentView>> replies = new LinkedHashMap<>();
        if (!tops.isEmpty()) {
            params.addValue("ids", tops.stream().map(Row::id).toList());
            for (Row r : named.query(SELECT + "WHERE cm.parent_id IN (:ids) AND cm.status = 'VISIBLE'" + block
                    + " ORDER BY cm.id", params, CommentService::map)) {
                replies.computeIfAbsent(r.parentId(), k -> new ArrayList<>()).add(view(r, viewer, List.of()));
            }
        }
        List<CommentView> items = tops.stream()
                .map(r -> view(r, viewer, replies.getOrDefault(r.id(), List.of())))
                .toList();
        long t = total == null ? 0 : total;
        return new CommentPage(items, p, (int) ((t + size - 1) / size), t);
    }

    @Transactional
    public CommentView create(long postId, long userId, CommentWriteRequest req) {
        PostRow post = findPost(postId);
        Viewer viewer = policy.requireReadable(post, userId);
        if (viewer.admin()) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_ALLOWED);
        }
        if (post.isNotice()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_ALLOWED);
        }
        String content = validContent(req == null ? null : req.content());
        Long parentId = null;
        Long replyTo = null;
        if (req.parentId() != null) {
            Row target = find(req.parentId());
            if (target == null || target.postId() != postId || !"VISIBLE".equals(target.status())) {
                throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
            }
            parentId = target.parentId() != null ? target.parentId() : target.id();
            replyTo = target.authorId();
        }
        long id = insert(postId, userId, parentId, replyTo, content);
        if (replyTo == null) {
            notifyIfOther(post.authorId(), userId, NotificationType.POST_COMMENT, id,
                    "'" + post.title() + "' 글에 새 댓글이 달렸습니다.");
        } else {
            notifyIfOther(replyTo, userId, NotificationType.COMMENT_REPLY, id, "내 댓글에 답글이 달렸습니다.");
        }
        return view(find(id), viewer, List.of());
    }

    /** 작성자 본인만. 고치면 "수정됨"(edited). */
    @Transactional
    public CommentView update(long commentId, long userId, CommentEditRequest req) {
        Row c = findVisible(commentId);
        Viewer viewer = policy.requireReadable(findPost(c.postId()), userId);
        if (c.authorId() != userId) {
            throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
        }
        String content = validContent(req == null ? null : req.content());
        jdbc.update("UPDATE comments SET content = ?, edited = TRUE, updated_at = ? WHERE id = ?", content,
                LocalDateTime.now(clock), commentId);
        return view(find(commentId), viewer, List.of());
    }

    /** 작성자와 그 블로그의 POST_MANAGE 권한자(D-100 #11). 메인 관리자 숨김·삭제는 005(ADM-03). */
    @Transactional
    public void delete(long commentId, long userId) {
        Row c = findVisible(commentId);
        Viewer viewer = policy.requireReadable(findPost(c.postId()), userId);
        if (c.authorId() != userId && !viewer.postManager()) {
            throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        // 누가 지웠는지 남겨 005 관리자 복구가 작성자가 지운 댓글을 가린다(005 T057, D-103 #11)
        jdbc.update("UPDATE comments SET status = 'DELETED', deleted_at = ?, deleted_by = ?, updated_at = ? WHERE id = ?",
                now, userId, now, commentId);
    }

    private CommentView view(Row r, Viewer viewer, List<CommentView> replies) {
        if ("HIDDEN".equals(r.status())) {
            return new CommentView(r.id(), r.parentId(), false, null, null, null, false, false, false, r.createdAt(),
                    replies, true);
        }
        if (!"VISIBLE".equals(r.status())) {
            return new CommentView(r.id(), r.parentId(), true, null, null, null, false, false, false, r.createdAt(),
                    replies, false);
        }
        String replyTo = null;
        if (r.replyToUserId() != null) {
            replyTo = "WITHDRAWN".equals(r.replyToStatus()) ? AuthorView.WITHDRAWN_NAME : r.replyToNickname();
        }
        boolean mine = viewer.is(r.authorId());
        return new CommentView(r.id(), r.parentId(), false, r.content(),
                authorView.ofComment(r.authorId(), r.nickname(), r.userStatus(), r.profileName()), replyTo, r.edited(),
                mine, mine || viewer.postManager(), r.createdAt(), replies, false);
    }

    private void notifyIfOther(long recipientId, long actorId, NotificationType type, long commentId, String content) {
        if (recipientId == actorId) {
            return;
        }
        String status = jdbc.query("SELECT status FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null,
                recipientId);
        if (!"ACTIVE".equals(status)) {
            return;
        }
        notifications.write(recipientId, type, "COMMENT", commentId, actorId, content, type.name() + ":" + commentId);
    }

    private String validContent(String raw) {
        String content = raw == null ? "" : raw.strip();
        if (content.isEmpty()) {
            throw new InvalidInputException("content", "NotBlank");
        }
        if (content.codePointCount(0, content.length()) > props.comment().maxLength()) {
            throw new InvalidInputException("content", "Size");
        }
        return content;
    }

    private long insert(long postId, long authorId, Long parentId, Long replyTo, String content) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO comments (parent_id, author_id, post_id, "
                    + "reply_to_user_id, content, edited, status, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, FALSE, 'VISIBLE', ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, parentId);
            ps.setLong(2, authorId);
            ps.setLong(3, postId);
            ps.setObject(4, replyTo);
            ps.setString(5, content);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private PostRow findPost(long postId) {
        return store.find(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private Row find(long id) {
        List<Row> rows = jdbc.query(SELECT + "WHERE cm.id = ?", CommentService::map, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Row findVisible(long id) {
        Row c = find(id);
        if (c == null || !"VISIBLE".equals(c.status())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return c;
    }

    private static Row map(ResultSet rs, int i) throws SQLException {
        return new Row(rs.getLong("id"), rs.getObject("parent_id", Long.class), rs.getLong("post_id"),
                rs.getLong("author_id"), rs.getObject("reply_to_user_id", Long.class), rs.getString("content"),
                rs.getBoolean("edited"), rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("nickname"), rs.getString("user_status"), rs.getString("profile_name"),
                rs.getString("reply_nickname"), rs.getString("reply_status"));
    }
}
