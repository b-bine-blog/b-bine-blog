package com.mainblog.auth.oauth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainblog.auth.security.AuthCookies;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.config.OAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 소셜 로그인 진행 정보(state·PKCE·로그인 유지·연결할 회원)를 서버 세션 대신 서명한 쿠키에 둔다 (008 FR-007, SCL-01).
 * 서버를 여러 대로 늘려도(4.3) 그대로 동작한다. 서명이 맞고 10분(설정값) 안일 때만 읽고, 돌아오면 바로 지운다(한 번만 씀).
 * 내용은 문자열 값만 JSON으로 담아 역직렬화 공격 여지를 두지 않는다.
 */
public class CookieAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE = "OAUTH2_REQ";
    /** 지운 진행 정보를 성공·실패 처리기가 읽을 수 있게 요청에 남겨 둔다. */
    public static final String REMOVED_ATTRIBUTE = CookieAuthorizationRequestRepository.class.getName() + ".removed";

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final byte[] key;
    private final Duration ttl;
    private final boolean secure;
    private final Clock clock;

    public CookieAuthorizationRequestRepository(ObjectMapper objectMapper, AuthProperties auth,
                                                OAuthProperties properties, AuthCookies cookies, Clock clock) {
        this.objectMapper = objectMapper;
        this.key = hmac(auth.jwtSecret().getBytes(StandardCharsets.UTF_8),
                "oauth2-authorization-request".getBytes(StandardCharsets.UTF_8));
        this.ttl = properties.requestTtl();
        this.secure = cookies.secure();
        this.clock = clock;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String value = AuthCookies.read(request, COOKIE);
        if (value == null) {
            return null;
        }
        int dot = value.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        try {
            byte[] payload = DEC.decode(value.substring(0, dot));
            byte[] signature = DEC.decode(value.substring(dot + 1));
            if (!MessageDigest.isEqual(signature, hmac(key, payload))) {
                return null;
            }
            Map<String, Object> map = objectMapper.readValue(payload, MAP);
            long exp = ((Number) map.get("exp")).longValue();
            if (clock.instant().getEpochSecond() > exp) {
                return null;
            }
            return fromMap(map);
        } catch (RuntimeException | java.io.IOException e) {
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            write(response, "", Duration.ZERO);
            return;
        }
        try {
            Map<String, Object> map = toMap(authorizationRequest);
            map.put("exp", clock.instant().plus(ttl).getEpochSecond());
            byte[] payload = objectMapper.writeValueAsBytes(map);
            write(response, ENC.encodeToString(payload) + "." + ENC.encodeToString(hmac(key, payload)), ttl);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("소셜 로그인 진행 정보를 저장하지 못했습니다", e);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
        write(response, "", Duration.ZERO);
        if (loaded != null) {
            request.setAttribute(REMOVED_ATTRIBUTE, loaded);
        }
        return loaded;
    }

    private void write(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax") // 소셜 서비스에서 돌아오는 최상위 GET에는 실린다
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static Map<String, Object> toMap(OAuth2AuthorizationRequest r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("authorizationUri", r.getAuthorizationUri());
        map.put("clientId", r.getClientId());
        map.put("redirectUri", r.getRedirectUri());
        map.put("scopes", List.copyOf(r.getScopes()));
        map.put("state", r.getState());
        map.put("additionalParameters", strings(r.getAdditionalParameters()));
        map.put("attributes", strings(r.getAttributes()));
        map.put("authorizationRequestUri", r.getAuthorizationRequestUri());
        return map;
    }

    @SuppressWarnings("unchecked")
    private static OAuth2AuthorizationRequest fromMap(Map<String, Object> map) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri((String) map.get("authorizationUri"))
                .clientId((String) map.get("clientId"))
                .redirectUri((String) map.get("redirectUri"))
                .scopes(new LinkedHashSet<>((List<String>) map.get("scopes")))
                .state((String) map.get("state"))
                .additionalParameters(new HashMap<>((Map<String, Object>) map.get("additionalParameters")))
                .attributes(new HashMap<>((Map<String, Object>) map.get("attributes")))
                .authorizationRequestUri((String) map.get("authorizationRequestUri"))
                .build();
    }

    private static Map<String, String> strings(Map<String, Object> source) {
        Map<String, String> out = new LinkedHashMap<>();
        source.forEach((k, v) -> {
            if (v != null) {
                out.put(k, String.valueOf(v));
            }
        });
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256을 쓸 수 없습니다", e);
        }
    }
}
