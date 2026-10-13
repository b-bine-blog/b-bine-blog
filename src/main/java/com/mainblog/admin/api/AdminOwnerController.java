package com.mainblog.admin.api;

import com.mainblog.admin.application.OwnerRevocationService;
import com.mainblog.admin.application.OwnerRevocationService.Revocation;
import com.mainblog.admin.application.OwnerWarningService;
import com.mainblog.admin.application.OwnerWarningService.WarningList;
import com.mainblog.admin.application.OwnerWarningService.WarningResult;
import com.mainblog.auth.security.AuthenticatedMember;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 블로그장 경고·바로 강퇴 (005 T031, ADM-07, FR-020~FR-029, contracts/admin-api.yaml owner). */
@RestController
public class AdminOwnerController {

    public record WarningRequest(Long ownerId, String reason) {}

    public record RevocationRequest(Long ownerId, String reason) {}

    private final OwnerWarningService warningService;
    private final OwnerRevocationService revocationService;

    public AdminOwnerController(OwnerWarningService warningService, OwnerRevocationService revocationService) {
        this.warningService = warningService;
        this.revocationService = revocationService;
    }

    @GetMapping("/api/admin/blogs/{blogId}/owner-warnings")
    public WarningList warnings(@PathVariable long blogId, @AuthenticationPrincipal AuthenticatedMember me) {
        return warningService.list(me.id(), blogId);
    }

    @PostMapping("/api/admin/blogs/{blogId}/owner-warnings")
    @ResponseStatus(HttpStatus.CREATED)
    public WarningResult warn(@PathVariable long blogId, @RequestBody(required = false) WarningRequest body,
                              @AuthenticationPrincipal AuthenticatedMember me) {
        WarningRequest r = body == null ? new WarningRequest(null, null) : body;
        return warningService.warn(me.id(), blogId, r.ownerId(), r.reason());
    }

    @PostMapping("/api/admin/blogs/{blogId}/owner-revocation")
    @ResponseStatus(HttpStatus.CREATED)
    public Revocation revoke(@PathVariable long blogId, @RequestBody(required = false) RevocationRequest body,
                             @AuthenticationPrincipal AuthenticatedMember me) {
        RevocationRequest r = body == null ? new RevocationRequest(null, null) : body;
        return revocationService.revokeDirect(me.id(), blogId, r.ownerId(), r.reason());
    }
}
