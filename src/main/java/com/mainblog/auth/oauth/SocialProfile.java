package com.mainblog.auth.oauth;

import java.util.Locale;
import java.util.Map;

/**
 * 소셜 서비스가 준 회원 정보 중 저장하는 두 가지(008 FR-002, FR-015): 고유 번호와 확인된 이메일.
 * 서비스가 확인하지 않은 이메일은 받지 않은 것과 같게 null로 둔다(경계 상황).
 */
public record SocialProfile(SocialProvider provider, String providerUserId, String verifiedEmail) {

    @SuppressWarnings("unchecked")
    public static SocialProfile from(SocialProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            // OpenID Connect: sub, email, email_verified
            case GOOGLE -> new SocialProfile(provider, text(attributes.get("sub")),
                    Boolean.TRUE.equals(attributes.get("email_verified")) ? email(attributes.get("email")) : null);
            // 카카오: id, kakao_account.email. 비즈 앱이 아니면 이메일이 없다(FR-016)
            case KAKAO -> {
                Map<String, Object> account = attributes.get("kakao_account") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : Map.of();
                boolean verified = Boolean.TRUE.equals(account.get("is_email_valid"))
                        && Boolean.TRUE.equals(account.get("is_email_verified"));
                yield new SocialProfile(provider, text(attributes.get("id")),
                        verified ? email(account.get("email")) : null);
            }
            // 네이버: response.id, response.email(이메일 제공 동의를 필수로 둔다)
            case NAVER -> {
                Map<String, Object> response = attributes.get("response") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : Map.of();
                yield new SocialProfile(provider, text(response.get("id")), email(response.get("email")));
            }
        };
    }

    private static String text(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("소셜 회원 고유 번호가 없습니다");
        }
        String s = String.valueOf(value);
        if (s.isBlank() || s.length() > 100) {
            throw new IllegalArgumentException("소셜 회원 고유 번호 형식이 아닙니다");
        }
        return s;
    }

    private static String email(Object value) {
        if (!(value instanceof String s) || s.isBlank() || s.length() > 255 || !s.contains("@")) {
            return null;
        }
        return s.strip().toLowerCase(Locale.ROOT);
    }
}
