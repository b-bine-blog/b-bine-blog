package com.mainblog.auth.oauth;

import com.mainblog.auth.oauth.SocialLoginService.Connections;
import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보의 소셜 연결 관리 (008 FR-010). 연결은 /oauth2/authorization/{id}?mode=link로 시작한다. */
@RestController
@RequestMapping("/api/members/me/social")
public class MemberSocialController {

    private final SocialLoginService service;
    private final SocialClients clients;

    public MemberSocialController(SocialLoginService service, SocialClients clients) {
        this.service = service;
        this.clients = clients;
    }

    @GetMapping
    public ConnectionsResponse connections(@AuthenticationPrincipal AuthenticatedMember member) {
        Connections c = service.connections(member.id());
        return new ConnectionsResponse(c.hasPassword(),
                c.accounts().stream().map(a -> new Account(a.provider().registrationId(), a.connectedAt())).toList(),
                clients.providers().stream().map(SocialProvider::registrationId).toList());
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal AuthenticatedMember member,
                                       @PathVariable String provider) {
        SocialProvider p = SocialProvider.fromRegistrationId(provider)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        service.unlink(member.id(), p);
        return ResponseEntity.noContent().build();
    }

    public record Account(String provider, LocalDateTime connectedAt) {}

    public record ConnectionsResponse(boolean hasPassword, List<Account> accounts, List<String> available) {}
}
