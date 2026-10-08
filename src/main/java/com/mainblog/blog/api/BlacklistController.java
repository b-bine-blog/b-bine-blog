package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.AppealService;
import com.mainblog.blog.application.AppealService.ApplicantView;
import com.mainblog.blog.application.AppealService.ManagerView;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 블랙리스트 해제 문의 (T070, BLG-12, FR-061~FR-063). 블랙리스트 열람은 SanctionController. */
@RestController
public class BlacklistController {

    private final AppealService appealService;

    public BlacklistController(AppealService appealService) {
        this.appealService = appealService;
    }

    @PostMapping("/api/blogs/{blogId}/blacklist-appeals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicantView create(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member,
                                @jakarta.validation.Valid @RequestBody(required = false) AppealRequest req) {
        return appealService.create(blogId, member.id(), req == null ? null : req.content());
    }

    @GetMapping("/api/blogs/{blogId}/blacklist-appeals")
    @PreAuthorize("@blogAuth.has(#blogId, 'MEMBER_MANAGE')")
    public Items list(@PathVariable Long blogId, @RequestParam(required = false) String status) {
        return new Items(appealService.list(blogId, status));
    }

    @PostMapping("/api/blogs/{blogId}/blacklist-appeals/{appealId}/release")
    public ManagerView release(@PathVariable Long blogId, @PathVariable Long appealId,
                               @AuthenticationPrincipal AuthenticatedMember member) {
        return appealService.release(blogId, appealId, member.id());
    }

    @PostMapping("/api/blogs/{blogId}/blacklist-appeals/{appealId}/rejection")
    public ManagerView reject(@PathVariable Long blogId, @PathVariable Long appealId,
                              @AuthenticationPrincipal AuthenticatedMember member) {
        return appealService.reject(blogId, appealId, member.id());
    }

    public record AppealRequest(@Size(max = 2000) String content) {}

    public record Items(List<ManagerView> items) {}
}
