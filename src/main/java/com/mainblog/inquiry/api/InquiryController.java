package com.mainblog.inquiry.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.common.ratelimit.ClientIpResolver;
import com.mainblog.inquiry.application.InquiryService;
import com.mainblog.inquiry.application.InquiryService.MyInquiry;
import com.mainblog.inquiry.application.InquiryService.Page;
import com.mainblog.inquiry.application.InquiryService.SubmitRequest;
import com.mainblog.inquiry.application.InquiryService.Submitted;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 시스템 문의 보내기(회원·비회원)와 내 문의(회원) (ADM-08, D-112, api-contracts/inquiry-api.yaml). */
@RestController
public class InquiryController {

    private final InquiryService service;
    private final ClientIpResolver ipResolver;

    public InquiryController(InquiryService service, ClientIpResolver ipResolver) {
        this.service = service;
        this.ipResolver = ipResolver;
    }

    @PostMapping("/api/inquiries")
    @ResponseStatus(HttpStatus.CREATED)
    public Submitted submit(@RequestBody SubmitRequest body, @AuthenticationPrincipal AuthenticatedMember me,
                            HttpServletRequest request) {
        return service.submit(me == null ? null : me.id(), body, ipResolver.resolve(request));
    }

    @GetMapping("/api/inquiries/mine")
    public Page<MyInquiry> mine(@RequestParam(required = false) Integer page,
                                @AuthenticationPrincipal AuthenticatedMember me) {
        return service.mine(me.id(), page);
    }
}
