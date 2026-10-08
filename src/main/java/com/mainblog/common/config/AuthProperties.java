package com.mainblog.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 로그인·인증 설정값 (T006). 숫자 규칙은 코드에 두지 않고 application.yml + 환경변수로 받는다(D-68).
 * 값의 근거는 specs/001-member-auth/data-model.md "설정값" 표(D-97, SEC-03, SEC-13, USR-02, USR-08).
 */
@Validated
@ConfigurationProperties("auth")
public record AuthProperties(
        @NotBlank String jwtSecret,
        @NotBlank String codeHashSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration rememberMeTtl,
        @NotNull Duration idleTimeout,
        @NotNull Duration extendInterval,
        @Valid @NotNull Login login,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Signup signup,
        @Valid @NotNull Code code,
        @Valid @NotNull Reset reset,
        @Valid @NotNull FindEmail findEmail,
        @Valid @NotNull Turnstile turnstile) {

    /** 로그인 실패: 3번째부터 사람 확인(SEC-13), 5번째에 5분 잠금(SEC-03). */
    public record Login(@Positive int captchaAfter, @Positive int lockAfter, @NotNull Duration lockDuration) {}

    /** IP당 요청 제한(SCL-01, R-06). */
    public record RateLimit(@Valid @NotNull Window login, @Valid @NotNull Window findEmail) {}

    public record Window(@Positive int limit, @NotNull Duration window) {}

    /** 가입 인증번호 10분(USR-02), 인증 뒤 가입 마감 30분(D-97). */
    public record Signup(@NotNull Duration codeTtl, @NotNull Duration verifiedTtl) {}

    /** 인증번호 공통: 다시 보내기 1분, 5번 틀리면 무효(USR-02, SEC-05). */
    public record Code(@NotNull Duration resendInterval, @Positive int maxFail) {}

    /** 비밀번호 재설정 인증번호 30분(SEC-05). */
    public record Reset(@NotNull Duration codeTtl) {}

    /** 이메일 찾기 임시 토큰 10분(USR-08). */
    public record FindEmail(@NotNull Duration tokenTtl) {}

    /** Cloudflare Turnstile(D-79). 키가 비어 있으면 사람 확인을 통과시키지 않는다. */
    public record Turnstile(String siteKey, String secretKey, @NotBlank String verifyUrl) {}
}
