package com.mainblog.board.application;

import com.mainblog.board.application.BoardViews.PostDetail;
import com.mainblog.board.application.BoardViews.PostSummary;
import com.mainblog.board.application.PostPolicy.Viewer;
import com.mainblog.board.application.PostStore.PostRow;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.markdown.MarkdownRenderer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 상세 (004 T017, FR-005, FR-016, FR-020, FR-047, research R-04, R-05).
 * 본문은 읽을 때마다 걸러 contentHtml로 준다. 마크다운 원문은 고칠 수 있는 사람(수정 화면)에게만 준다.
 */
@Service
public class PostQueryService {

    private final PostStore store;
    private final PostPolicy policy;
    private final PostSummaryReader summaryReader;
    private final MarkdownRenderer renderer;
    private final ViewCountService viewCount;
    private final JdbcTemplate jdbc;

    public PostQueryService(PostStore store, PostPolicy policy, PostSummaryReader summaryReader,
                            MarkdownRenderer renderer, ViewCountService viewCount, JdbcTemplate jdbc) {
        this.store = store;
        this.policy = policy;
        this.summaryReader = summaryReader;
        this.renderer = renderer;
        this.viewCount = viewCount;
        this.jdbc = jdbc;
    }

    /** 글을 열어 본다. 볼 수 있으면 조회수를 센 뒤 상세를 준다. */
    @Transactional
    public PostDetail open(long postId, Long userId, HttpServletRequest request, HttpServletResponse response) {
        PostRow post = store.find(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Viewer viewer = policy.requireReadableForDetail(post, userId);
        if (!post.isHiddenByAdmin()) {
            viewCount.record(post, userId, request, response);
        }
        return detail(post, viewer);
    }

    /** 쓰기·수정 직후의 상세(조회수를 세지 않는다). */
    @Transactional(readOnly = true)
    public PostDetail detailAfterWrite(long postId, Long userId) {
        PostRow post = store.find(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return detail(post, policy.requireReadable(post, userId));
    }

    PostDetail detail(PostRow post, Viewer viewer) {
        List<PostSummary> rows = summaryReader.list("p.id = ?", List.of(post.id()), "p.id", 1, 0);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        PostSummary s = rows.get(0);
        boolean hidden = post.isHiddenByAdmin(); // 작성자 본인만 여기 온다. 고치기·댓글·좋아요·공유는 막는다(005 T041)
        boolean canEdit = !hidden && PostPolicy.canEdit(post, viewer);
        boolean liked = viewer.userId() != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM post_likes WHERE post_id = ? AND user_id = ?)", Boolean.class,
                post.id(), viewer.userId()));
        boolean scrapped = viewer.userId() != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM post_scraps WHERE post_id = ? AND user_id = ?)", Boolean.class,
                post.id(), viewer.userId()));  // 스크랩 버튼 상태(D-134)
        boolean shareable = s.blog() == null || !"PRIVATE".equals(s.blog().visibility());
        return new PostDetail(s.id(), s.boardType(), s.blog(), s.title(), s.author(), s.categoryName(),
                s.thumbnailUrl(), s.likeCount(), s.viewCount(), s.commentCount(), s.createdAt(),
                canEdit ? post.content() : null, renderer.toSafeHtml(post.content()), post.categoryId(),
                store.tagsOf(post.id()), liked, canEdit, PostPolicy.canDelete(post, viewer),
                !hidden && PostPolicy.canComment(post, viewer), !hidden && shareable, hidden, scrapped);
    }
}
