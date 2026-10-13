package com.mainblog.admin.api;

import com.mainblog.admin.application.AdminActionQueryService;
import com.mainblog.admin.application.AdminActionQueryService.AdminAction;
import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.auth.security.AuthenticatedMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 활동 기록 보기 (005 T047, ADM-06, FR-039, contracts/admin-api.yaml action-log). 고치기·지우기 경로는 없다(D-103 #20). */
@RestController
public class AdminActionController {

    private final AdminActionQueryService service;

    public AdminActionController(AdminActionQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/actions")
    public Page<AdminAction> list(@RequestParam(required = false) String actionType,
                                  @RequestParam(required = false) Long adminId,
                                  @RequestParam(required = false) String targetType,
                                  @RequestParam(required = false) Long targetId,
                                  @RequestParam(required = false) String from,
                                  @RequestParam(required = false) String to,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size,
                                  @AuthenticationPrincipal AuthenticatedMember me) {
        return service.list(me.id(), actionType, adminId, targetType, targetId, from, to, page, size);
    }
}
