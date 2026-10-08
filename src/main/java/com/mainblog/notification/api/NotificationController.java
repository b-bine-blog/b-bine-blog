package com.mainblog.notification.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.notification.application.NotificationCommandService;
import com.mainblog.notification.application.NotificationQueryService;
import com.mainblog.notification.application.NotificationQueryService.NotificationSlice;
import com.mainblog.notification.application.NotificationQueryService.UnreadCount;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림함 (003 T011, SOC-04, contracts/social-api.yaml notification). 모두 로그인 필요, 본인 알림만.
 * unread-count는 화면이 30초마다 X-User-Activity 헤더 없이 부른다(FR-032, D-62, D-72).
 */
@RestController
public class NotificationController {

    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;

    public NotificationController(NotificationQueryService queryService, NotificationCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping("/api/notifications")
    public NotificationSlice list(@RequestParam(required = false) String tab,
                                  @RequestParam(required = false) Long before,
                                  @AuthenticationPrincipal AuthenticatedMember member) {
        return queryService.list(member.id(), tab, before);
    }

    @DeleteMapping("/api/notifications")
    public Deleted deleteAll(@RequestParam(required = false) String tab,
                             @AuthenticationPrincipal AuthenticatedMember member) {
        return new Deleted(commandService.deleteAll(member.id(), tab));
    }

    @GetMapping("/api/notifications/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal AuthenticatedMember member) {
        return queryService.unreadCount(member.id());
    }

    @DeleteMapping("/api/notifications/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long notificationId, @AuthenticationPrincipal AuthenticatedMember member) {
        commandService.delete(member.id(), notificationId);
    }

    @PostMapping("/api/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable long notificationId, @AuthenticationPrincipal AuthenticatedMember member) {
        commandService.read(member.id(), notificationId);
    }

    @PostMapping("/api/notifications/read-all")
    public Updated readAll(@RequestParam(required = false) String tab,
                           @AuthenticationPrincipal AuthenticatedMember member) {
        return new Updated(commandService.readAll(member.id(), tab));
    }

    public record Deleted(int deleted) {}

    public record Updated(int updated) {}
}
