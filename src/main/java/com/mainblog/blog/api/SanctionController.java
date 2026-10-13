package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.BlacklistService;
import com.mainblog.blog.application.BlacklistService.BlacklistEntry;
import com.mainblog.blog.application.BlogQueryService;
import com.mainblog.blog.application.BlogReportService;
import com.mainblog.blog.application.BlogReportService.ReportView;
import com.mainblog.blog.application.BlogReportService.Resolution;
import com.mainblog.blog.application.Paged;
import com.mainblog.blog.application.SanctionService;
import com.mainblog.blog.application.SanctionService.Command;
import com.mainblog.blog.application.SanctionService.History;
import com.mainblog.blog.application.SanctionService.SanctionView;
import com.mainblog.blog.domain.SanctionType;
import com.mainblog.blog.domain.SuspendPeriod;
import jakarta.validation.constraints.Size;
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

/** 제재·블로그 안 신고·블랙리스트 열람, 멤버 관리 권한 (T062~T064, BLG-11, BLG-13, FR-048~FR-059). */
@RestController
public class SanctionController {

    private final SanctionService sanctionService;
    private final BlogReportService reportService;
    private final BlacklistService blacklistService;
    private final BlogQueryService queryService;

    public SanctionController(SanctionService sanctionService, BlogReportService reportService,
                              BlacklistService blacklistService, BlogQueryService queryService) {
        this.sanctionService = sanctionService;
        this.reportService = reportService;
        this.blacklistService = blacklistService;
        this.queryService = queryService;
    }

    @PostMapping("/api/blogs/{blogId}/members/{userId}/sanctions")
    @ResponseStatus(HttpStatus.CREATED)
    public SanctionView sanction(@PathVariable Long blogId, @PathVariable Long userId,
                                 @AuthenticationPrincipal AuthenticatedMember member,
                                 @jakarta.validation.Valid @RequestBody SanctionRequest req) {
        return sanctionService.sanction(blogId, member.id(), userId,
                new Command(req.type(), req.period(), req.reason()));
    }

    @GetMapping("/api/blogs/{blogId}/members/{userId}/sanctions")
    public History history(@PathVariable Long blogId, @PathVariable Long userId) {
        return sanctionService.history(blogId, userId);
    }

    @GetMapping("/api/blogs/{blogId}/reports")
    @PreAuthorize("@blogAuth.has(#blogId, 'MEMBER_MANAGE')")
    public Paged<ReportView> reports(@PathVariable Long blogId, @RequestParam(required = false) String status,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer size) {
        return reportService.list(blogId, status, queryService.page(page), queryService.size(size));
    }

    @PostMapping("/api/blogs/{blogId}/reports/{reportId}/resolution")
    public ReportView resolve(@PathVariable Long blogId, @PathVariable Long reportId,
                              @AuthenticationPrincipal AuthenticatedMember member,
                              @jakarta.validation.Valid @RequestBody ResolutionRequest req) {
        return reportService.resolve(blogId, reportId, member.id(),
                new Resolution(req.result(), req.period(), req.reason()));
    }

    @GetMapping("/api/blogs/{blogId}/blacklist")
    @PreAuthorize("@blogAuth.has(#blogId, 'MEMBER_MANAGE')")
    public Paged<BlacklistEntry> blacklist(@PathVariable Long blogId, @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return blacklistService.list(blogId, queryService.page(page), queryService.size(size));
    }

    public record SanctionRequest(SanctionType type, SuspendPeriod period, @Size(max = 500) String reason) {}

    public record ResolutionRequest(@Size(max = 20) String result, SuspendPeriod period,
                                    @Size(max = 500) String reason) {}
}
