package com.mainblog.social.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.social.application.ReportReasons;
import com.mainblog.social.application.ReportReasons.Reasons;
import com.mainblog.social.application.ReportService;
import com.mainblog.social.application.ReportService.Accepted;
import com.mainblog.social.application.ReportService.ReportRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 신고 접수·사유 목록 (003 T035, SOC-06, contracts/social-api.yaml report). 로그인 필요. */
@RestController
public class ReportController {

    private final ReportService reportService;
    private final ReportReasons reasons;

    public ReportController(ReportService reportService, ReportReasons reasons) {
        this.reportService = reportService;
        this.reasons = reasons;
    }

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public Accepted report(@RequestBody ReportRequest request, @AuthenticationPrincipal AuthenticatedMember member) {
        return reportService.report(member.id(), request);
    }

    @GetMapping("/api/reports/reasons")
    public Reasons reasons() {
        return reasons.list();
    }
}
