package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.BlogClosingService;
import com.mainblog.blog.application.BlogViewService;
import com.mainblog.blog.application.BlogViews.BlogDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 블로그 폐쇄·철회, 블로그장만 (T051, BLG-09, FR-073, FR-078). */
@RestController
public class ClosingController {

    private final BlogClosingService closingService;
    private final BlogViewService viewService;

    public ClosingController(BlogClosingService closingService, BlogViewService viewService) {
        this.closingService = closingService;
        this.viewService = viewService;
    }

    @PostMapping("/api/blogs/{blogId}/closing")
    public BlogDetail close(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        closingService.closeByOwner(blogId, member.id());
        return viewService.byId(blogId);
    }

    @DeleteMapping("/api/blogs/{blogId}/closing")
    public BlogDetail withdraw(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        closingService.withdraw(blogId, member.id());
        return viewService.byId(blogId);
    }
}
