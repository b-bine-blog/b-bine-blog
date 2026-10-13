package com.mainblog.auth.oauth;

import com.mainblog.auth.application.TokenService.IssuedTokens;
import com.mainblog.auth.oauth.SocialLoginService.CallbackResult;
import com.mainblog.auth.security.AuthCookies;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.ratelimit.ClientIpResolver;
import com.mainblog.common.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * 소셜 서비스에서 돌아온 뒤 처리 (008 FR-002~FR-007, FR-013).
 * 성공하면 우리 로그인 쿠키(JWT)를 주고, 가입·연결을 기다리면 대기 토큰을 주소 조각(#)으로 넘겨 서버 기록에 남지 않게 한다.
 * Spring이 만든 소셜 로그인 인증 정보는 쓰지 않고 바로 버린다(서버 세션 없음, D-53).
 */
public class SocialLoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginHandlers.class);

    private final SocialLoginService service;
    private final AuthCookies cookies;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver ipResolver;
    private final AuthProperties authProperties;

    public SocialLoginHandlers(SocialLoginService service, AuthCookies cookies, RateLimiter rateLimiter,
                               ClientIpResolver ipResolver, AuthProperties authProperties) {
        this.service = service;
        this.cookies = cookies;
        this.rateLimiter = rateLimiter;
        this.ipResolver = ipResolver;
        this.authProperties = authProperties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        SecurityContextHolder.clearContext();
        Map<String, Object> attrs = requestAttributes(request);
        boolean linkMode = "true".equals(attrs.get(SocialAuthorizationRequestResolver.ATTR_LINK_MODE));

        AuthProperties.Window limit = authProperties.rateLimit().login();
        if (!rateLimiter.tryAcquire("login:" + ipResolver.resolve(request), limit.limit(), limit.window())) {
            redirectError(response, linkMode, ErrorCode.RATE_LIMITED);
            return;
        }

        CallbackResult result;
        try {
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            SocialProvider provider = SocialProvider.fromRegistrationId(token.getAuthorizedClientRegistrationId())
                    .orElseThrow();
            SocialProfile profile = SocialProfile.from(provider, token.getPrincipal().getAttributes());
            boolean remember = "true".equals(attrs.get(SocialAuthorizationRequestResolver.ATTR_REMEMBER));
            Long linkUser = attrs.get(SocialAuthorizationRequestResolver.ATTR_LINK_USER) instanceof String s
                    ? Long.valueOf(s) : null;
            result = service.onCallback(profile, remember, linkMode, linkUser);
        } catch (RuntimeException e) {
            log.warn("소셜 로그인 처리 실패: {}", e.toString());
            redirectError(response, linkMode, ErrorCode.SOCIAL_LOGIN_FAILED);
            return;
        }

        switch (result.outcome()) {
            case LOGGED_IN -> {
                write(response, result.tokens());
                response.sendRedirect("/");
            }
            case LINKED -> response.sendRedirect("/me/?social=linked");
            case PENDING -> response.sendRedirect("/social.html#t=" + result.pendingToken());
            case ERROR -> redirectError(response, linkMode, result.error());
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        boolean linkMode = "true".equals(requestAttributes(request).get(SocialAuthorizationRequestResolver.ATTR_LINK_MODE));
        boolean cancelled = exception instanceof OAuth2AuthenticationException e
                && "access_denied".equals(e.getError().getErrorCode());
        if (!cancelled) {
            log.info("소셜 로그인 실패: {}", exception.getMessage());
        }
        String code = cancelled ? "CANCELLED" : ErrorCode.SOCIAL_LOGIN_FAILED.name();
        response.sendRedirect((linkMode ? "/me/" : "/login.html") + "?socialError=" + code);
    }

    private static void redirectError(HttpServletResponse response, boolean linkMode, ErrorCode code)
            throws IOException {
        response.sendRedirect((linkMode ? "/me/" : "/login.html") + "?socialError=" + code.name());
    }

    private void write(HttpServletResponse response, IssuedTokens tokens) {
        cookies.write(response, tokens.accessToken(), tokens.refreshToken(), tokens.rememberMe(),
                tokens.refreshRemaining());
    }

    private static Map<String, Object> requestAttributes(HttpServletRequest request) {
        Object removed = request.getAttribute(CookieAuthorizationRequestRepository.REMOVED_ATTRIBUTE);
        return removed instanceof OAuth2AuthorizationRequest r ? r.getAttributes() : Map.of();
    }
}
