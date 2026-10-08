package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.BlogQueryService;
import com.mainblog.blog.application.MemberManageService;
import com.mainblog.blog.application.MemberManageService.MemberView;
import com.mainblog.blog.application.Paged;
import com.mainblog.common.error.InvalidInputException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 멤버 정보·부블로그장 지정·해제·블로그 탈퇴 (T057, T067, FR-039~FR-047, FR-064, FR-065). */
@RestController
public class MemberManageController {

    private final MemberManageService memberManageService;
    private final BlogQueryService queryService;

    public MemberManageController(MemberManageService memberManageService, BlogQueryService queryService) {
        this.memberManageService = memberManageService;
        this.queryService = queryService;
    }

    @GetMapping("/api/blogs/{blogId}/members")
    public Paged<MemberView> members(@PathVariable Long blogId, @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer size) {
        return memberManageService.listMembers(blogId, queryService.page(page), queryService.size(size));
    }

    @PutMapping("/api/blogs/{blogId}/managers/{userId}")
    public MemberView setManager(@PathVariable Long blogId, @PathVariable Long userId,
                                 @AuthenticationPrincipal AuthenticatedMember member,
                                 @RequestBody ManagerPermissions req) {
        if (req.blogInfo() == null || req.memberManage() == null || req.postManage() == null) {
            throw new InvalidInputException("permissions", "NotNull");
        }
        return memberManageService.setManager(blogId, member.id(), userId, req.blogInfo(), req.memberManage(),
                req.postManage());
    }

    @DeleteMapping("/api/blogs/{blogId}/managers/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeManager(@PathVariable Long blogId, @PathVariable Long userId,
                              @AuthenticationPrincipal AuthenticatedMember member) {
        memberManageService.removeManager(blogId, member.id(), userId);
    }

    @DeleteMapping("/api/blogs/{blogId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        memberManageService.leave(blogId, member.id());
    }

    public record ManagerPermissions(Boolean blogInfo, Boolean memberManage, Boolean postManage) {}
}
