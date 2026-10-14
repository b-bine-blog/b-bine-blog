package com.mainblog.admin.api;

import com.mainblog.admin.application.AdminInquiryService;
import com.mainblog.admin.application.AdminInquiryService.InquiryDetail;
import com.mainblog.admin.application.AdminInquiryService.InquirySummary;
import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.auth.security.AuthenticatedMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 시스템 문의함·답변 (ADM-08, D-112, api-contracts/inquiry-api.yaml). */
@RestController
public class AdminInquiryController {

    public record AnswerRequest(String answer) {}

    private final AdminInquiryService service;

    public AdminInquiryController(AdminInquiryService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/inquiries")
    public Page<InquirySummary> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer size,
                                     @AuthenticationPrincipal AuthenticatedMember me) {
        return service.list(me.id(), status, page, size);
    }

    @GetMapping("/api/admin/inquiries/{inquiryId}")
    public InquiryDetail detail(@PathVariable long inquiryId, @AuthenticationPrincipal AuthenticatedMember me) {
        return service.detail(me.id(), inquiryId);
    }

    @PostMapping("/api/admin/inquiries/{inquiryId}/answer")
    public InquiryDetail answer(@PathVariable long inquiryId, @RequestBody(required = false) AnswerRequest body,
                                @AuthenticationPrincipal AuthenticatedMember me) {
        return service.answer(me.id(), inquiryId, body == null ? null : body.answer());
    }
}
