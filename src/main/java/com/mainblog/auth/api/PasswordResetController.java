package com.mainblog.auth.api;

import com.mainblog.auth.application.PasswordResetService;
import com.mainblog.auth.application.PasswordResetService.ResetTarget;
import com.mainblog.auth.api.SignupController.EmailRequest;
import com.mainblog.common.error.InvalidInputException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 찾기·재설정 API (T055). 계약: contracts의 password-reset 태그.
 * verify와 저장은 email 또는 resetToken(이메일 찾기에서 온 경우, FR-038) 중 하나를 받는다.
 */
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService resetService;

    public PasswordResetController(PasswordResetService resetService) {
        this.resetService = resetService;
    }

    /** 가입·탈퇴 여부, 1분 제한과 관계없이 항상 202(FR-030, D-28). */
    @PostMapping("/code")
    public ResponseEntity<Void> sendCode(@Valid @RequestBody EmailRequest request) {
        resetService.sendCode(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@Valid @RequestBody VerifyRequest request) {
        resetService.verify(target(request.email(), request.resetToken()), request.code());
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest request) {
        resetService.reset(target(request.email(), request.resetToken()), request.code(), request.newPassword(),
                request.newPasswordConfirm());
        return ResponseEntity.noContent().build();
    }

    /** email과 resetToken 중 정확히 하나만 와야 한다. */
    private static ResetTarget target(String email, String resetToken) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasToken = resetToken != null && !resetToken.isBlank();
        if (hasEmail == hasToken) {
            throw new InvalidInputException(hasEmail ? "resetToken" : "email", hasEmail ? "Null" : "NotBlank");
        }
        return new ResetTarget(hasEmail ? email : null, hasToken ? resetToken : null);
    }

    public record VerifyRequest(@Email @Size(max = 255) String email,
                                @Size(max = 100) String resetToken,
                                @NotBlank @Pattern(regexp = "^\\d{6}$") String code) {}

    public record ResetRequest(@Email @Size(max = 255) String email,
                               @Size(max = 100) String resetToken,
                               @NotBlank @Pattern(regexp = "^\\d{6}$") String code,
                               @NotNull @Size(max = 100) String newPassword,
                               @NotNull @Size(max = 100) String newPasswordConfirm) {}
}
