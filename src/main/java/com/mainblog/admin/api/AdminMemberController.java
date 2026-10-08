package com.mainblog.admin.api;

import com.mainblog.admin.application.AdminMemberService;
import com.mainblog.admin.application.AdminMemberService.MemberDetail;
import com.mainblog.admin.application.AdminMemberService.MemberSummary;
import com.mainblog.admin.application.AdminMemberService.PrivacyView;
import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.auth.security.AuthenticatedMember;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 회원 조회 (005 T016, ADM-01, FR-005~FR-009, contracts/admin-api.yaml member). */
@RestController
public class AdminMemberController {

    private final AdminMemberService service;

    public AdminMemberController(AdminMemberService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/members")
    public Page<MemberSummary> search(@RequestParam(required = false) String type,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size,
                                      @AuthenticationPrincipal AuthenticatedMember me) {
        return service.search(me.id(), type, q, page, size);
    }

    @GetMapping("/api/admin/members/{userId}")
    public MemberDetail detail(@PathVariable long userId, @AuthenticationPrincipal AuthenticatedMember me) {
        return service.detail(me.id(), userId);
    }

    /** 원문 응답은 브라우저·프록시 캐시에 남지 않게 no-store(research R-04). */
    @PostMapping("/api/admin/members/{userId}/privacy-view")
    public ResponseEntity<PrivacyView> privacyView(@PathVariable long userId,
                                                   @AuthenticationPrincipal AuthenticatedMember me) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.privacyView(me.id(), userId));
    }
}
