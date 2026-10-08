package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.board.application.PostPolicy;
import com.mainblog.board.application.PostPolicy.Viewer;
import com.mainblog.board.application.PostStore.PostRow;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 글 권한표 (004 T012, data-model "권한표", FR-008~FR-010, D-90, D-100 #1·#19). */
class PostPolicyTest {

    private static final long AUTHOR = 1L;

    private static PostRow blogPost(boolean authorLeft) {
        return new PostRow(10L, 5L, AUTHOR, null, "BLOG", "t", "c", "VISIBLE", authorLeft, 0, 0, LocalDateTime.now());
    }

    private static PostRow notice() {
        return new PostRow(11L, null, 9L, null, "NOTICE", "t", "c", "VISIBLE", false, 0, 0, LocalDateTime.now());
    }

    private static final Viewer GUEST = Viewer.anonymous();
    private static final Viewer AUTHOR_MEMBER = new Viewer(AUTHOR, false, true, false);
    private static final Viewer OTHER_MEMBER = new Viewer(2L, false, true, false);
    private static final Viewer POST_MANAGER = new Viewer(3L, false, true, true);
    private static final Viewer OUTSIDER = new Viewer(4L, false, false, false);
    private static final Viewer ADMIN = new Viewer(5L, true, false, false);

    @Test
    void 수정은_지금_멤버인_작성자만() {
        assertThat(PostPolicy.canEdit(blogPost(false), AUTHOR_MEMBER)).isTrue();
        assertThat(PostPolicy.canEdit(blogPost(true), new Viewer(AUTHOR, false, false, false))).isFalse();
        assertThat(PostPolicy.canEdit(blogPost(false), POST_MANAGER)).isFalse();
        assertThat(PostPolicy.canEdit(blogPost(false), ADMIN)).isFalse();
        assertThat(PostPolicy.canEdit(blogPost(false), GUEST)).isFalse();
    }

    @Test
    void 삭제는_작성자_글_관리_권한자_관리자() {
        assertThat(PostPolicy.canDelete(blogPost(false), AUTHOR_MEMBER)).isTrue();
        assertThat(PostPolicy.canDelete(blogPost(false), POST_MANAGER)).isTrue();
        assertThat(PostPolicy.canDelete(blogPost(false), ADMIN)).isTrue();
        assertThat(PostPolicy.canDelete(blogPost(false), OTHER_MEMBER)).isFalse();
        assertThat(PostPolicy.canDelete(blogPost(false), OUTSIDER)).isFalse();
        assertThat(PostPolicy.canDelete(blogPost(true), new Viewer(AUTHOR, false, false, false))).isFalse();
    }

    @Test
    void 공지는_관리자_누구나_고치고_지운다() {
        assertThat(PostPolicy.canEdit(notice(), ADMIN)).isTrue();
        assertThat(PostPolicy.canDelete(notice(), ADMIN)).isTrue();
        assertThat(PostPolicy.canEdit(notice(), POST_MANAGER)).isFalse();
        assertThat(PostPolicy.canDelete(notice(), AUTHOR_MEMBER)).isFalse();
    }

    @Test
    void 댓글_좋아요는_로그인한_관리자_아닌_회원이_블로그_글에만() {
        assertThat(PostPolicy.canComment(blogPost(false), OUTSIDER)).isTrue();
        assertThat(PostPolicy.canLike(blogPost(false), OTHER_MEMBER)).isTrue();
        assertThat(PostPolicy.canComment(blogPost(false), GUEST)).isFalse();
        assertThat(PostPolicy.canComment(blogPost(false), ADMIN)).isFalse();
        assertThat(PostPolicy.canComment(notice(), OUTSIDER)).isFalse();
        assertThat(PostPolicy.canLike(notice(), OUTSIDER)).isFalse();
    }
}
