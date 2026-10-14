package com.mainblog.board.application;

import com.mainblog.board.application.BoardViews.PostPage;
import com.mainblog.board.application.PostPolicy.Viewer;
import com.mainblog.board.application.PostStore.PostRow;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 메뉴의 작성글·작성댓글·스크랩 (D-134). 모두 본인 것만, 지금 볼 수 있는 글에 달린 것만 보인다.
 * 스크랩은 한 회원 한 글에 하나(DB 유니크). 다시 누르면 취소. 공지와 메인 관리자는 스크랩하지 않는다.
 */
@Service
public class MyActivityService {

    /** 보는 회원이 지금 열 수 있는 블로그 글(PUBLIC·LINK, PRIVATE는 멤버만). 파라미터: 보는 회원 번호 1번. 별칭 posts p, blogs b. */
    private static final String READABLE_BLOG_POST = "p.status = 'VISIBLE' AND p.board_type = 'BLOG' "
            + "AND b.status IN ('ACTIVE', 'CLOSING') AND b.hidden = FALSE "
            + "AND (b.visibility IN ('PUBLIC', 'LINK') "
            + "OR EXISTS (SELECT 1 FROM blog_members vm WHERE vm.blog_id = p.blog_id AND vm.user_id = ?))";

    public record ScrapResult(boolean scrapped) {}

    public record MyComment(long id, String content, LocalDateTime createdAt, long postId, String postTitle,
                            String blogAddress, String blogName) {}

    public record MyCommentPage(List<MyComment> items, int page, int size, long totalElements, int totalPages) {}

    private final PostStore store;
    private final PostPolicy policy;
    private final PostSummaryReader reader;
    private final BoardPaging paging;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MyActivityService(PostStore store, PostPolicy policy, PostSummaryReader reader, BoardPaging paging,
                             JdbcTemplate jdbc, Clock clock) {
        this.store = store;
        this.policy = policy;
        this.reader = reader;
        this.paging = paging;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 스크랩 누르기·취소. 볼 수 없는 글은 404, 공지는 400, 메인 관리자는 403. */
    @Transactional
    public ScrapResult toggleScrap(long postId, long userId) {
        PostRow post = store.find(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Viewer viewer = policy.requireReadable(post, userId);
        if (viewer.admin()) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_ALLOWED);
        }
        if (post.isNotice()) {
            throw new BusinessException(ErrorCode.SCRAP_NOT_ALLOWED);
        }
        if (jdbc.update("DELETE FROM post_scraps WHERE post_id = ? AND user_id = ?", postId, userId) > 0) {
            return new ScrapResult(false);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("INSERT IGNORE INTO post_scraps (user_id, post_id, created_at, updated_at) VALUES (?, ?, ?, ?)",
                userId, postId, now, now);
        return new ScrapResult(true);
    }

    /** 스크랩한 글. 스크랩한 순서 최신부터. 지워졌거나 이제 볼 수 없는 글은 빠진다. */
    @Transactional(readOnly = true)
    public PostPage scraps(long userId, Integer page, Integer size) {
        List<Object> args = new ArrayList<>(List.of(userId, userId));
        String where = READABLE_BLOG_POST
                + " AND EXISTS (SELECT 1 FROM post_scraps ps WHERE ps.post_id = p.id AND ps.user_id = ?)";
        String order = "(SELECT ps.id FROM post_scraps ps WHERE ps.post_id = p.id AND ps.user_id = ?) DESC";  // 스크랩한 순서
        return reader.page(where, args, order, List.of(userId), paging.page(page), paging.size(size));
    }

    /** 내가 쓴 블로그 글. 최신순. 떠난 블로그에 남은 글("탈퇴한 계정")은 뺀다. */
    @Transactional(readOnly = true)
    public PostPage posts(long userId, Integer page, Integer size) {
        List<Object> args = new ArrayList<>(List.of(userId, userId));
        String where = READABLE_BLOG_POST + " AND p.author_id = ? AND p.author_left = FALSE";
        return reader.page(where, args, "p.created_at DESC, p.id DESC", paging.page(page), paging.size(size));
    }

    /** 내가 쓴 댓글. 최신순. 지운 댓글과 지금 볼 수 없는 글의 댓글은 뺀다. */
    @Transactional(readOnly = true)
    public MyCommentPage comments(long userId, Integer page, Integer size) {
        int p = paging.page(page);
        int s = paging.size(size);
        String from = " FROM comments c JOIN posts p ON p.id = c.post_id LEFT JOIN blogs b ON b.id = p.blog_id "
                + "WHERE c.author_id = ? AND c.status = 'VISIBLE' AND ((p.status = 'VISIBLE' AND p.board_type = 'NOTICE') OR ("
                + READABLE_BLOG_POST + "))";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, userId, userId);
        List<MyComment> items = jdbc.query("SELECT c.id, c.content, c.created_at, p.id AS post_id, p.title, "
                + "b.address, b.name" + from + " ORDER BY c.created_at DESC, c.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new MyComment(rs.getLong("id"), rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime(), rs.getLong("post_id"), rs.getString("title"),
                        rs.getString("address"), rs.getString("name")),
                userId, userId, s, (long) (p - 1) * s);
        long count = total == null ? 0 : total;
        return new MyCommentPage(items, p, s, count, (int) ((count + s - 1) / s));
    }
}
