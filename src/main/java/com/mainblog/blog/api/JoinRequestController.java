package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.BlogQueryService;
import com.mainblog.blog.application.JoinService;
import com.mainblog.blog.application.JoinService.JoinRequestView;
import com.mainblog.blog.application.JoinService.PendingRequestView;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 참여 신청 (T034, BLG-04, BLG-05, FR-017~FR-024). 승인·거절은 멤버 관리 권한. */
@RestController
public class JoinRequestController {

    private final JoinService joinService;
    private final BlogQueryService queryService;

    public JoinRequestController(JoinService joinService, BlogQueryService queryService) {
        this.joinService = joinService;
        this.queryService = queryService;
    }

    @PostMapping("/api/blogs/{blogId}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinRequestView apply(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        return joinService.apply(blogId, member.id());
    }

    @GetMapping("/api/blogs/{blogId}/join-requests")
    @PreAuthorize("@blogAuth.has(#blogId, 'MEMBER_MANAGE')")
    public PageResponse<PendingRequestView> pending(@PathVariable Long blogId,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        int p = queryService.page(page);
        int s = queryService.size(size);
        return PageResponse.of(joinService.pending(blogId, p, s), p, s);
    }

    @PostMapping("/api/blogs/{blogId}/join-requests/{requestId}/approval")
    @PreAuthorize("@blogAuth.has(#blogId, 'MEMBER_MANAGE')")
    public JoinRequestView approve(@PathVariable Long blogId, @PathVariable Long requestId,
                                   @AuthenticationPrincipal AuthenticatedMember member) {
        return joinService.approve(blogId, requestId, member.id());
    }

    @PostMapping("/api/blogs/{blogId}/join-requests/{requestId}/rejection")
    @PreAuthorize("@blogAuth.has(#blogId, 'MEMBER_MANAGE')")
    public JoinRequestView reject(@PathVariable Long blogId, @PathVariable Long requestId,
                                  @AuthenticationPrincipal AuthenticatedMember member) {
        return joinService.reject(blogId, requestId, member.id());
    }

    @DeleteMapping("/api/me/join-requests/{requestId}")
    public ResponseEntity<Void> cancel(@PathVariable Long requestId,
                                       @AuthenticationPrincipal AuthenticatedMember member) {
        joinService.cancel(requestId, member.id());
        return ResponseEntity.noContent().build();
    }

    /** 목록 응답(items, page, size, totalCount). */
    public record PageResponse<T>(List<T> items, int page, int size, long totalCount) {
        static <T> PageResponse<T> of(Page<T> p, int page, int size) {
            return new PageResponse<>(p.getContent(), page, size, p.getTotalElements());
        }
    }
}
