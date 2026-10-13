package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthCookies;
import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.BlogAccessPolicy;
import com.mainblog.blog.application.ShareLinkService;
import com.mainblog.blog.application.ShareLinkService.ShareLinkView;
import com.mainblog.blog.domain.Blog;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 일부 공개 블로그의 공유 링크 (T042, BLG-01, FR-026~FR-029, research R-06). */
@RestController
public class ShareLinkController {

    private final ShareLinkService shareLinkService;
    private final AuthCookies authCookies;

    public ShareLinkController(ShareLinkService shareLinkService, AuthCookies authCookies) {
        this.authCookies = authCookies;
        this.shareLinkService = shareLinkService;
    }

    @GetMapping("/api/blogs/{blogId}/share-link")
    public ShareLinkView current(@PathVariable Long blogId) {
        return shareLinkService.current(blogId);
    }

    @PostMapping("/api/blogs/{blogId}/share-link")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareLinkView regenerate(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        return shareLinkService.regenerate(blogId, member.id());
    }

    /**
     * 링크 값이 유효하면 BLOG_SHARE_{blogId} 쿠키를 굽는다. 브라우저를 닫으면 사라지는 세션 쿠키이고,
     * 링크를 다시 만들면 값이 달라져 바로 막힌다(FR-028). 자동 구독하지 않는다(FR-029).
     */
    @PostMapping("/api/blogs/share-access")
    public ShareAccessResponse access(@jakarta.validation.Valid @RequestBody ShareAccessRequest req,
                                      HttpServletResponse response) {
        Blog blog = shareLinkService.resolve(req.token());
        ResponseCookie cookie = ResponseCookie.from(BlogAccessPolicy.shareCookieName(blog.getId()), req.token())
                .httpOnly(true)
                .secure(authCookies.secure())
                .sameSite("Lax")
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new ShareAccessResponse(blog.getId(), blog.getAddress());
    }

    public record ShareAccessRequest(@Size(max = 100) String token) {}

    public record ShareAccessResponse(Long blogId, String address) {}
}
