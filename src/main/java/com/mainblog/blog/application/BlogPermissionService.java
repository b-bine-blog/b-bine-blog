package com.mainblog.blog.application;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogMember;
import com.mainblog.blog.domain.BlogMemberRepository;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 블로그별 역할·권한 확인 (T014, SEC-07, SEC-11, FR-034~FR-036, research R-01).
 * 요청마다 blog_members와 진행 중 정지를 DB에서 읽는다. 토큰에는 회원 번호만 있다(D-53).
 * 컨트롤러는 {@code @PreAuthorize("@blogAuth.has(#blogId, 'MEMBER_MANAGE')")}로, 서비스는 {@link #require}로 쓴다.
 * 순서: 블로그 없음·폐쇄 → 404, 그 블로그에서 정지 → 403 BLOG_SUSPENDED, 볼 수 없음 → 404, 권한 없음 → 403 BLOG_FORBIDDEN.
 */
@Component("blogAuth")
public class BlogPermissionService {

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogAccessPolicy accessPolicy;
    private final SuspensionQuery suspensionQuery;
    private final Clock clock;

    public BlogPermissionService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
                                 BlogAccessPolicy accessPolicy, SuspensionQuery suspensionQuery, Clock clock) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.accessPolicy = accessPolicy;
        this.suspensionQuery = suspensionQuery;
        this.clock = clock;
    }

    /** @PreAuthorize용. 권한이 없으면 false(403), 블로그가 없거나 정지면 예외로 그 응답을 준다. */
    public boolean has(Long blogId, String permission) {
        BlogContext ctx = context(blogId);
        return allows(ctx.member(), BlogPermission.valueOf(permission));
    }

    /** 확인하고 블로그·역할을 돌려준다. 권한이 없으면 403 BLOG_FORBIDDEN. */
    public BlogContext require(Long blogId, BlogPermission permission) {
        BlogContext ctx = context(blogId);
        if (!allows(ctx.member(), permission)) {
            throw new BusinessException(ErrorCode.BLOG_FORBIDDEN);
        }
        return ctx;
    }

    /** 이미 읽은(잠근) 블로그로 다시 확인한다. 운영 동작은 블로그 행을 잠근 뒤 이걸로 역할을 다시 본다. */
    public BlogContext require(Blog blog, Long userId, BlogPermission permission) {
        BlogContext ctx = context(blog, userId);
        if (!allows(ctx.member(), permission)) {
            throw new BusinessException(ErrorCode.BLOG_FORBIDDEN);
        }
        return ctx;
    }

    /** 볼 수 있는지까지 확인한 블로그와 역할(로그인하지 않았으면 member·userId null). */
    public BlogContext context(Long blogId) {
        Blog blog = blogRepository.findById(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        return context(blog, currentUserId());
    }

    public BlogContext context(Blog blog, Long userId) {
        if (blog.isClosed()) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        BlogMember member = null;
        if (userId != null) {
            LocalDateTime now = LocalDateTime.now(clock);
            suspensionQuery.find(blog.getId(), userId, now).ifPresent(s -> {
                throw new BusinessException(ErrorCode.BLOG_SUSPENDED)
                        .with("suspendedUntil", s.endsAt())
                        .with("reason", s.reason());
            });
            member = memberRepository.findByBlogIdAndUserId(blog.getId(), userId).orElse(null);
        }
        if (!accessPolicy.canView(blog, member, userId)) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        return new BlogContext(blog, member, userId);
    }

    /** 권한표(data-model.md). 블로그장은 모두, 부블로그장은 켜진 영역만, 멤버는 MEMBER까지. */
    public static boolean allows(BlogMember m, BlogPermission p) {
        if (p == BlogPermission.VIEW) {
            return true;
        }
        if (m == null) {
            return false;
        }
        if (m.isOwner()) {
            return true;
        }
        return switch (p) {
            case VIEW, MEMBER -> true;
            case BLOG_INFO -> m.isManager() && m.isPermBlogInfo();
            case MEMBER_MANAGE -> m.isManager() && m.isPermMemberManage();
            case POST_MANAGE -> m.isManager() && m.isPermPostManage();
            case OWNER -> false;
        };
    }

    /** 화면 메뉴용 권한 목록(BlogDetail.myPermissions). VIEW·MEMBER는 넣지 않는다. */
    public static Set<BlogPermission> permissionsOf(BlogMember m) {
        Set<BlogPermission> result = EnumSet.noneOf(BlogPermission.class);
        for (BlogPermission p : EnumSet.of(BlogPermission.BLOG_INFO, BlogPermission.MEMBER_MANAGE,
                BlogPermission.POST_MANAGE, BlogPermission.OWNER)) {
            if (allows(m, p)) {
                result.add(p);
            }
        }
        return result;
    }

    /** 다른 멤버 글 삭제는 글 관리 권한(FR-037). 글 API(004)가 쓴다. */
    public boolean canDeletePost(Long blogId, Long authorId) {
        BlogContext ctx = context(blogId);
        return ctx.userId() != null && (ctx.userId().equals(authorId) || allows(ctx.member(), BlogPermission.POST_MANAGE));
    }

    /** 글 수정은 작성자 본인만. 블로그장도 다른 멤버 글은 고칠 수 없다(FR-037, D-58). */
    public boolean canEditPost(Long blogId, Long authorId) {
        BlogContext ctx = context(blogId);
        return ctx.member() != null && ctx.userId().equals(authorId);
    }

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedMember m) {
            return m.id();
        }
        return null;
    }
}
