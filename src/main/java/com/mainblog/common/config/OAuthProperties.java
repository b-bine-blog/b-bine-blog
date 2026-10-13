package com.mainblog.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 소셜 로그인 설정값 (008 FR-004, FR-005, FR-007, FR-014, D-68, D-125).
 * 앱 키는 환경 변수로만 받고, 키가 비어 있는 서비스는 버튼을 숨기고 로그인도 받지 않는다.
 */
@Validated
@ConfigurationProperties("oauth")
public record OAuthProperties(
        @Valid @NotNull Client google,
        @Valid @NotNull Client kakao,
        @Valid @NotNull Client naver,
        @NotNull Duration requestTtl,
        @NotNull Duration signupTtl,
        @NotNull Duration linkTtl) {

    public record Client(String clientId, String clientSecret) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }
}
