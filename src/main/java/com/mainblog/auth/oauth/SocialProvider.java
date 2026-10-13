package com.mainblog.auth.oauth;

import java.util.Locale;
import java.util.Optional;

/** 소셜 로그인 서비스 (008 FR-001, D-125). registrationId는 /oauth2/authorization/{id} 주소에 쓴다. */
public enum SocialProvider {
    GOOGLE, KAKAO, NAVER;

    public String registrationId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SocialProvider> fromRegistrationId(String id) {
        for (SocialProvider p : values()) {
            if (p.registrationId().equals(id)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
