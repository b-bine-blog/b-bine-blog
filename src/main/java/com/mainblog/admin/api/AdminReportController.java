package com.mainblog.admin.api;

import com.mainblog.admin.application.AdminReportService;
import com.mainblog.admin.application.AdminReportService.ReportDetail;
import com.mainblog.admin.application.AdminReportService.ReportSummary;
import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.auth.security.AuthenticatedMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 신고함·신고 처리 (005 T035, ADM-04, FR-030~FR-034, contracts/admin-api.yaml report). */
@RestController
public class AdminReportController {

    public record ResolutionRequest(String result, String reason) {}

    private final AdminReportService service;

    public AdminReportController(AdminReportService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/reports")
    public Page<ReportSummary> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size,
                                    @AuthenticationPrincipal AuthenticatedMember me) {
        return service.list(me.id(), status, page, size);
    }

    @GetMapping("/api/admin/reports/{reportId}")
    public ReportDetail detail(@PathVariable long reportId, @AuthenticationPrincipal AuthenticatedMember me) {
        return service.detail(me.id(), reportId);
    }

    @PostMapping("/api/admin/reports/{reportId}/resolution")
    public ReportDetail resolve(@PathVariable long reportId, @RequestBody(required = false) ResolutionRequest body,
                                @AuthenticationPrincipal AuthenticatedMember me) {
        ResolutionRequest r = body == null ? new ResolutionRequest(null, null) : body;
        return service.resolve(me.id(), reportId, r.result(), r.reason());
    }
}
