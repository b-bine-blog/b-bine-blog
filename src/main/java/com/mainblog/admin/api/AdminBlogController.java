package com.mainblog.admin.api;

import com.mainblog.admin.application.AdminBlogService;
import com.mainblog.admin.application.AdminBlogService.BlogDetail;
import com.mainblog.admin.application.AdminBlogService.BlogSummary;
import com.mainblog.admin.application.AdminBlogService.CloseResult;
import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.auth.security.AuthenticatedMember;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 블로그 조회·숨김·강제 폐쇄 (005 T024, ADM-02, FR-011~FR-013, contracts/admin-api.yaml blog). */
@RestController
public class AdminBlogController {

    private final AdminBlogService service;

    public AdminBlogController(AdminBlogService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/blogs")
    public Page<BlogSummary> search(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size,
                                    @AuthenticationPrincipal AuthenticatedMember me) {
        return service.search(me.id(), q, status, page, size);
    }

    @GetMapping("/api/admin/blogs/{blogId}")
    public BlogDetail detail(@PathVariable long blogId, @AuthenticationPrincipal AuthenticatedMember me) {
        return service.detail(me.id(), blogId);
    }

    @PutMapping("/api/admin/blogs/{blogId}/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@PathVariable long blogId, @AuthenticationPrincipal AuthenticatedMember me) {
        service.hide(me.id(), blogId);
    }

    @DeleteMapping("/api/admin/blogs/{blogId}/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unhide(@PathVariable long blogId, @AuthenticationPrincipal AuthenticatedMember me) {
        service.unhide(me.id(), blogId);
    }

    @PostMapping("/api/admin/blogs/{blogId}/force-close")
    public CloseResult forceClose(@PathVariable long blogId, @AuthenticationPrincipal AuthenticatedMember me) {
        return service.forceClose(me.id(), blogId);
    }
}
