package com.mainblog.board.application;

import com.mainblog.board.application.BoardViews.LikeResult;
import com.mainblog.board.application.PostPolicy.Viewer;
import com.mainblog.board.application.PostStore.PostRow;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import com.mainblog.common.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 누르기·취소 (004 T038, FR-042~FR-044, SC-006, D-100 #12·#13, research R-09).
 * 한 회원 한 글 하나는 DB 유니크가 지킨다. like_count는 실제로 바뀐 행 수만큼만 바꾼다.
 * 처음 누를 때만 작성자에게 알린다(event_key가 같아 다시 눌러도 알림이 다시 생기지 않는다).
 */
@Service
public class LikeService {

    private final PostStore store;
    private final PostPolicy policy;
    private final JdbcTemplate jdbc;
    private final RateLimiter rateLimiter;
    private final NotificationWriter notifications;
    private final BoardProperties props;
    private final Clock clock;

    public LikeService(PostStore store, PostPolicy policy, JdbcTemplate jdbc, RateLimiter rateLimiter,
                       NotificationWriter notifications, BoardProperties props, Clock clock) {
        this.store = store;
        this.policy = policy;
        this.jdbc = jdbc;
        this.rateLimiter = rateLimiter;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public LikeResult toggle(long postId, long userId) {
        if (!rateLimiter.tryAcquire("post-like:" + userId, props.like().rateLimit(), props.like().rateWindow())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED)
                    .with("retryAfterSeconds", rateLimiter.retryAfterSeconds("post-like:" + userId,
                            props.like().rateWindow()));
        }
        // 같은 글의 누르기·취소를 차례로 처리한다. 동시에 눌러도 행과 like_count가 어긋나지 않는다(SC-006).
        PostRow post = store.findForUpdate(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Viewer viewer = policy.requireReadable(post, userId);
        if (viewer.admin()) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_ALLOWED);
        }
        if (post.isNotice()) {
            throw new BusinessException(ErrorCode.LIKE_NOT_ALLOWED);
        }
        boolean liked;
        int removed = jdbc.update("DELETE FROM post_likes WHERE post_id = ? AND user_id = ?", postId, userId);
        if (removed > 0) {
            jdbc.update("UPDATE posts SET like_count = like_count - ? WHERE id = ?", removed, postId);
            liked = false;
        } else {
            LocalDateTime now = LocalDateTime.now(clock);
            int added = jdbc.update("INSERT IGNORE INTO post_likes (user_id, post_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?)", userId, postId, now, now);
            if (added > 0) {
                jdbc.update("UPDATE posts SET like_count = like_count + 1 WHERE id = ?", postId);
                notifyAuthor(post, userId);
            }
            liked = true;
        }
        Integer count = jdbc.queryForObject("SELECT like_count FROM posts WHERE id = ?", Integer.class, postId);
        return new LikeResult(liked, count == null ? 0 : count);
    }

    private void notifyAuthor(PostRow post, long userId) {
        if (post.authorId() == userId) {
            return;
        }
        String status = jdbc.query("SELECT status FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null,
                post.authorId());
        if (!"ACTIVE".equals(status)) {
            return;
        }
        notifications.write(post.authorId(), NotificationType.POST_LIKE, "POST", post.id(), userId,
                "'" + post.title() + "' 글에 좋아요가 눌렸습니다.", "POST_LIKE:" + post.id() + ":" + userId);
    }
}
