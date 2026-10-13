package com.mainblog.auth.api;

import com.mainblog.auth.application.FindEmailService;
import com.mainblog.auth.application.FindEmailService.FoundAccount;
import com.mainblog.common.ratelimit.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이메일 찾기 API (T070). 계약: contracts의 find-email 태그. */
@RestController
@RequestMapping("/api/auth/find-email")
public class FindEmailController {

    private final FindEmailService findEmailService;
    private final ClientIpResolver ipResolver;

    public FindEmailController(FindEmailService findEmailService, ClientIpResolver ipResolver) {
        this.findEmailService = findEmailService;
        this.ipResolver = ipResolver;
    }

    /** 일치하는 계정이 없으면 accounts가 빈 배열이다. */
    @PostMapping
    public FindEmailResponse find(@Valid @RequestBody FindEmailRequest body, HttpServletRequest request) {
        return new FindEmailResponse(findEmailService.find(body.name(), body.phone(), ipResolver.resolve(request)));
    }

    @PostMapping("/reset-code")
    public ResponseEntity<Void> sendResetCode(@Valid @RequestBody ResetCodeRequest body) {
        findEmailService.sendResetCode(body.resetToken());
        return ResponseEntity.accepted().build();
    }

    public record FindEmailRequest(@NotNull @Size(max = 100) String name, @NotNull @Size(max = 30) String phone) {}

    public record FindEmailResponse(List<FoundAccount> accounts) {}

    public record ResetCodeRequest(@NotBlank @Size(max = 100) String resetToken) {}
}
