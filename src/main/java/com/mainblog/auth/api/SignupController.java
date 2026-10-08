package com.mainblog.auth.api;

import com.mainblog.auth.application.SignupService;
import com.mainblog.auth.application.SignupService.SignupCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.mainblog.common.config.ClockConfig;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 가입 API (T031). 계약: contracts/member-auth-api.yaml의 signup 태그. */
@RestController
@RequestMapping("/api/auth/signup")
public class SignupController {

    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    /** ① 인증번호 발송. 가입 여부·1분 제한과 관계없이 항상 202(FR-006, D-28). */
    @PostMapping("/email-code")
    public ResponseEntity<Void> sendCode(@Valid @RequestBody EmailRequest request) {
        signupService.sendCode(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/email-code/verify")
    public VerifyResponse verify(@Valid @RequestBody CodeVerifyRequest request) {
        return new VerifyResponse(signupService.verifyCode(request.email(), request.code())
                .atZone(ClockConfig.ZONE).toOffsetDateTime());
    }

    @PostMapping
    public ResponseEntity<Void> signUp(@Valid @RequestBody SignupRequest request) {
        signupService.signUp(new SignupCommand(request.email(), request.password(), request.name(),
                request.nickname(), request.phone(), request.agreeTerms(), request.agreePrivacy()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 255) String email) {}

    public record CodeVerifyRequest(@NotBlank @Email @Size(max = 255) String email,
                                    @NotBlank @Pattern(regexp = "^\\d{6}$") String code) {}

    public record VerifyResponse(OffsetDateTime verifiedUntil) {}

    public record SignupRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank String password,
            @NotBlank String name,
            @NotBlank String nickname,
            @NotBlank String phone,
            @NotNull Boolean agreeTerms,
            @NotNull Boolean agreePrivacy) {}
}
