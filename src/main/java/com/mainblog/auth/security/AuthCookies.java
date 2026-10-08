package com.mainblog.auth.security;

import com.mainblog.common.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 로그인 쿠키 (T037, FR-026, R-03). 모두 HttpOnly·Secure·SameSite=Lax.
 * "로그인 유지"를 체크하지 않으면 Max-Age가 없는 세션 쿠키라 브라우저를 닫으면 사라진다(FR-023).
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "AT";
    public static final String REFRESH = "RT";

    private final AuthProperties properties;

    public AuthCookies(AuthProperties properties) {
        this.properties = properties;
    }

    /**
     * @param refreshRemaining 로그인 유지일 때 RT 쿠키의 남은 시간(만료 시각까지). 유지하지 않으면 무시한다.
     */
    public void write(HttpServletResponse response, String accessToken, String refreshToken, boolean rememberMe,
                      Duration refreshRemaining) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(ACCESS, accessToken, rememberMe ? properties.accessTokenTtl() : null).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(REFRESH, refreshToken, rememberMe ? refreshRemaining : null).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH, "", Duration.ZERO).toString());
    }

    public static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && !c.getValue().isEmpty()) {
                return c.getValue();
            }
        }
        return null;
    }

    private static ResponseCookie cookie(String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/");
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }
}
