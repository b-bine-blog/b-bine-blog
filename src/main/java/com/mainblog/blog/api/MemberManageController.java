package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.MemberManageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 블로그 탈퇴 (T057, BLG-07, FR-064, FR-065). 멤버 목록·부블로그장 API는 US8에서 더한다. */
@RestController
public class MemberManageController {

    private final MemberManageService memberManageService;

    public MemberManageController(MemberManageService memberManageService) {
        this.memberManageService = memberManageService;
    }

    @DeleteMapping("/api/blogs/{blogId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        memberManageService.leave(blogId, member.id());
    }
}
