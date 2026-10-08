package com.mainblog.notification.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.notification.application.NotificationSettingsService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 내 알림 설정 (003 T030, contracts/social-api.yaml notification-settings). 본인만, 경로에 회원 번호가 없다. */
@RestController
public class NotificationSettingsController {

    private final NotificationSettingsService settingsService;

    public NotificationSettingsController(NotificationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/api/me/notification-settings")
    public Map<String, Object> get(@AuthenticationPrincipal AuthenticatedMember member) {
        return settingsService.get(member.id());
    }

    /** 다른 키를 400으로 거부하려고 본문을 Map으로 받는다(additionalProperties: false). */
    @PutMapping("/api/me/notification-settings")
    public Map<String, Object> update(@RequestBody Map<String, Object> body,
                                      @AuthenticationPrincipal AuthenticatedMember member) {
        return settingsService.update(member.id(), body);
    }
}
