package com.mainblog.blog.application;

import com.mainblog.auth.security.CodeHasher;
import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.blog.domain.BlogShareLink;
import com.mainblog.blog.domain.BlogShareLinkRepository;
import com.mainblog.blog.domain.Visibility;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일부 공개 블로그의 공유 링크 (T042, FR-026~FR-028, FR-032, research R-06).
 * 링크 값은 주소와 별개인 32바이트 무작위 값이다. 블로그당 유효한 링크는 하나이고, 새로 만들면 이전 링크는 막힌다.
 */
@Service
public class ShareLinkService {

    private final BlogShareLinkRepository repository;
    private final BlogRepository blogRepository;
    private final BlogPermissionService permissionService;
    private final CodeHasher codeHasher;

    public ShareLinkService(BlogShareLinkRepository repository, BlogRepository blogRepository,
                            BlogPermissionService permissionService, CodeHasher codeHasher) {
        this.repository = repository;
        this.blogRepository = blogRepository;
        this.permissionService = permissionService;
        this.codeHasher = codeHasher;
    }

    /** 이전 링크를 막고 새로 만든다. 블로그 생성·공개 범위 변경·다시 만들기에서 부른다. */
    public BlogShareLink issue(long blogId) {
        deactivate(blogId);
        return repository.save(new BlogShareLink(blogId, codeHasher.newRandomToken()));
    }

    public void deactivate(long blogId) {
        repository.findFirstByBlogIdAndActiveTrue(blogId).ifPresent(link -> {
            link.deactivate();
            repository.flush();
        });
    }

    /** 일부 공개 블로그를 볼 수 있는 사람에게 지금 링크를 보여준다. 공개·비공개 블로그는 링크가 없다(404). */
    @Transactional(readOnly = true)
    public ShareLinkView current(long blogId) {
        BlogContext ctx = permissionService.require(blogId, BlogPermission.VIEW);
        if (ctx.blog().getVisibility() != Visibility.LINK) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        BlogShareLink link = repository.findFirstByBlogIdAndActiveTrue(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return view(ctx.blog(), link);
    }

    /** 블로그장만 다시 만든다(FR-028). */
    @Transactional
    public ShareLinkView regenerate(long blogId, long userId) {
        Blog blog = blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        permissionService.require(blog, userId, BlogPermission.OWNER);
        if (blog.getVisibility() != Visibility.LINK) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return view(blog, issue(blogId));
    }

    /** 링크로 들어온 사람. 유효하지 않거나 폐쇄·일부 공개가 아닌 블로그면 404. */
    @Transactional(readOnly = true)
    public Blog resolve(String token) {
        BlogShareLink link = token == null ? null : repository.findByTokenAndActiveTrue(token).orElse(null);
        if (link == null) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        Blog blog = blogRepository.findById(link.getBlogId())
                .filter(b -> !b.isClosed() && b.getVisibility() == Visibility.LINK)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        return blog;
    }

    private static ShareLinkView view(Blog blog, BlogShareLink link) {
        return new ShareLinkView("/blog/" + blog.getAddress() + "?share=" + link.getToken(), link.getToken(),
                link.getCreatedAt());
    }

    public record ShareLinkView(String url, String token, LocalDateTime createdAt) {}
}
