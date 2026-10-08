package com.mainblog.admin.api;

import com.mainblog.admin.application.AdminStatsService;
import com.mainblog.admin.application.AdminStatsService.Stats;
import com.mainblog.auth.security.AuthenticatedMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 통계 (005 T051, ADM-05, FR-040, contracts/admin-api.yaml stats). */
@RestController
public class AdminStatsController {

    private final AdminStatsService service;

    public AdminStatsController(AdminStatsService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/stats")
    public Stats stats(@AuthenticationPrincipal AuthenticatedMember me) {
        return service.stats(me.id());
    }
}
