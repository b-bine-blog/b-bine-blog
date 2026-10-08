package com.mainblog.blog.application;

import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogMember;
import com.mainblog.blog.domain.BlogShareLinkRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 블로그를 볼 수 있는지 (T015, FR-025~FR-030, research R-06).
 * PUBLIC 누구나, LINK 멤버·구독자·유효한 공유 링크 쿠키, PRIVATE 멤버만. 폐쇄 여부와 정지는 BlogPermissionService가 본다.
 * 공유 링크로 들어온 것은 쿠키 + DB로만 판단해 서버에 상태를 두지 않는다(SCL-01).
 */
@Component
public class BlogAccessPolicy {

    public static final String SHARE_COOKIE_PREFIX = "BLOG_SHARE_";

    private final JdbcTemplate jdbc;
    private final BlogShareLinkRepository shareLinkRepository;

    public BlogAccessPolicy(JdbcTemplate jdbc, BlogShareLinkRepository shareLinkRepository) {
        this.jdbc = jdbc;
        this.shareLinkRepository = shareLinkRepository;
    }

    public boolean canView(Blog blog, BlogMember member, Long userId) {
        if (member != null) {
            return true;
        }
        return switch (blog.getVisibility()) {
            case PUBLIC -> true;
            case PRIVATE -> false;
            case LINK -> (userId != null && isSubscriber(blog.getId(), userId)) || hasValidShareCookie(blog.getId());
        };
    }

    public static String shareCookieName(long blogId) {
        return SHARE_COOKIE_PREFIX + blogId;
    }

    boolean isSubscriber(long blogId, long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM blog_subscriptions WHERE blog_id = ? AND user_id = ?",
                Integer.class, blogId, userId);
        return n != null && n > 0;
    }

    /** 다시 만든 링크와 값이 다르면 이전 쿠키는 바로 막힌다(FR-028). */
    boolean hasValidShareCookie(long blogId) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return false;
        }
        HttpServletRequest request = attrs.getRequest();
        if (request.getCookies() == null) {
            return false;
        }
        String name = shareCookieName(blogId);
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName()) && c.getValue() != null) {
                return shareLinkRepository.findByTokenAndActiveTrue(c.getValue())
                        .filter(link -> link.getBlogId().equals(blogId))
                        .isPresent();
            }
        }
        return false;
    }
}
