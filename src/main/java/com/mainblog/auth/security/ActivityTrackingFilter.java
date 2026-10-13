package com.mainblog.auth.security;

import com.mainblog.auth.application.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 사용자가 직접 한 행동만 활동으로 센다 (T041, FR-024, D-62, R-04).
 * 화면이 붙인 X-User-Activity: 1 요청에서만 그 기기의 RT 활동 시각을 갱신한다. 알림 폴링은 헤더가 없어 세지 않는다.
 */
public class ActivityTrackingFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-User-Activity";

    private final TokenService tokenService;

    public ActivityTrackingFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("1".equals(request.getHeader(HEADER))) {
            String refreshToken = AuthCookies.read(request, AuthCookies.REFRESH);
            if (refreshToken != null) {
                tokenService.touch(refreshToken);
            }
        }
        chain.doFilter(request, response);
    }
}
