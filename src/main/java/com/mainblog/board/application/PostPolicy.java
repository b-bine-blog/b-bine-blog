package com.mainblog.board.application;

import com.mainblog.blog.application.BlogContext;
import com.mainblog.blog.application.BlogPermission;
import com.mainblog.blog.application.BlogPermissionService;
import com.mainblog.board.application.PostStore.PostRow;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 글 권한 (004 T012, FR-002, FR-008~FR-010, FR-036, FR-042, D-90, D-100 #1·#9·#19, data-model "권한표").
 * 블로그 역할·열람·정지는 요청마다 002 {@link BlogPermissionService}로 DB에서 확인한다(SEC-07).
 * 판단 자체는 {@link Viewer}만 받는 정적 메서드라서 단위 테스트로 표를 확인한다.
 */
@Component
public class PostPolicy {

    private final BlogPermissionService blogAuth;
    private final JdbcTemplate jdbc;

    public PostPolicy(BlogPermissionService blogAuth, JdbcTemplate jdbc) {
        this.blogAuth = blogAuth;
        this.jdbc = jdbc;
    }

    /** 글을 보는 사람. member·postManager는 그 글의 블로그에서의 역할. */
    public record Viewer(Long userId, boolean admin, boolean member, boolean postManager) {

        public static Viewer anonymous() {
            return new Viewer(null, false, false, false);
        }

        boolean is(long id) {
            return userId != null && userId == id;
        }
    }

    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        String role = jdbc.query("SELECT role FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, userId);
        return "ADMIN".equals(role);
    }

    /** 블로그 글 쓰기·이미지 올리기: 그 블로그 멤버(정지 아님)만. 관리자는 403 ADMIN_NOT_ALLOWED(D-90). 폐쇄 예정도 된다. */
    public BlogContext requireWriter(long blogId, long userId) {
        if (isAdmin(userId)) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_ALLOWED);
        }
        BlogContext ctx = blogAuth.context(blogId);
        if (ctx.member() == null) {
            throw new BusinessException(ErrorCode.BLOG_FORBIDDEN);
        }
        return ctx;
    }

    /** 공지 쓰기·이미지: 메인 관리자만. */
    public void requireAdmin(long userId) {
        if (!isAdmin(userId)) {
            throw new BusinessException(ErrorCode.ADMIN_ONLY);
        }
    }

    /**
     * 볼 수 있는 글인지 확인하고 보는 사람을 돌려준다. 없는 글·못 보는 글은 404 POST_NOT_FOUND, 그 블로그에서 정지면 403.
     */
    public Viewer requireReadable(PostRow post, Long userId) {
        if (!post.isVisible()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        boolean admin = isAdmin(userId);
        if (post.isNotice()) {
            return new Viewer(userId, admin, false, false);
        }
        return viewerIn(blogContext(post.blogId()), admin);
    }

    /** 삭제 확인용 보는 사람. 메인 관리자는 블로그 공개 범위와 관계없이 지울 수 있다. */
    public Viewer viewerForDelete(PostRow post, Long userId) {
        if (!post.isVisible()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        boolean admin = isAdmin(userId);
        if (admin || post.isNotice()) {
            return new Viewer(userId, admin, false, false);
        }
        return viewerIn(blogContext(post.blogId()), false);
    }

    private BlogContext blogContext(long blogId) {
        try {
            return blogAuth.context(blogId);
        } catch (BusinessException e) {
            if (e.errorCode() == ErrorCode.BLOG_NOT_FOUND) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
            throw e;
        }
    }

    private static Viewer viewerIn(BlogContext ctx, boolean admin) {
        return new Viewer(ctx.userId(), admin, ctx.member() != null,
                BlogPermissionService.allows(ctx.member(), BlogPermission.POST_MANAGE));
    }

    /** 수정: 블로그 글은 작성자 본인이면서 지금 그 블로그 멤버(D-100 #1). 공지는 메인 관리자 누구나(D-100 #19). */
    public static boolean canEdit(PostRow post, Viewer v) {
        if (post.isNotice()) {
            return v.admin();
        }
        return v.member() && v.is(post.authorId()) && !post.authorLeft();
    }

    /** 삭제: 작성자(지금 멤버), POST_MANAGE 권한자, 메인 관리자(FR-009, FR-010). */
    public static boolean canDelete(PostRow post, Viewer v) {
        if (post.isNotice()) {
            return v.admin();
        }
        return v.admin() || v.postManager() || canEdit(post, v);
    }

    /** 댓글: 글을 볼 수 있는 로그인 회원. 관리자 X(D-90), 공지 X(D-100 #19). 공유 링크로 보는 회원도 된다(D-100 #9). */
    public static boolean canComment(PostRow post, Viewer v) {
        return !post.isNotice() && v.userId() != null && !v.admin();
    }

    /** 좋아요: 댓글과 같은 사람. */
    public static boolean canLike(PostRow post, Viewer v) {
        return canComment(post, v);
    }
}
