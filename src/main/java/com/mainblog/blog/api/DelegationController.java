package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.DelegationService;
import com.mainblog.blog.application.DelegationService.DelegationView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 블로그장 위임 요청·보기·수락·거절 (T056, BLG-08, FR-066~FR-071). */
@RestController
public class DelegationController {

    private final DelegationService delegationService;

    public DelegationController(DelegationService delegationService) {
        this.delegationService = delegationService;
    }

    @PostMapping("/api/blogs/{blogId}/delegation")
    @ResponseStatus(HttpStatus.CREATED)
    public DelegationView request(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member,
                                  @RequestBody DelegationRequest req) {
        return delegationService.request(blogId, member.id(), req.toUserId());
    }

    @GetMapping("/api/blogs/{blogId}/delegation")
    public ResponseEntity<DelegationView> pending(@PathVariable Long blogId,
                                                  @AuthenticationPrincipal AuthenticatedMember member) {
        return delegationService.pending(blogId, member.id()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/blogs/{blogId}/delegation/acceptance")
    public DelegationView accept(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        return delegationService.accept(blogId, member.id());
    }

    @PostMapping("/api/blogs/{blogId}/delegation/rejection")
    public DelegationView reject(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        return delegationService.reject(blogId, member.id());
    }

    public record DelegationRequest(Long toUserId) {}
}
