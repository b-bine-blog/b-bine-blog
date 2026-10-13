package com.mainblog.auth.oauth;

import com.mainblog.auth.security.AuthCookies;
import com.mainblog.auth.security.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 소셜 로그인 시작 (008 FR-001, FR-006, FR-007, FR-010).
 * GET /oauth2/authorization/{google|kakao|naver}?remember=1&mode=link
 * - remember: 로그인 창의 "로그인 유지" 체크를 진행 정보에 담는다.
 * - mode=link: 내 정보에서 연결할 때. 지금 로그인한 회원 번호를 AT 쿠키에서 읽어 담는다.
 * 구글은 PKCE도 쓴다(FR-007). state는 모든 서비스에 Spring이 무작위로 만든다.
 */
public class SocialAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String BASE_URI = "/oauth2/authorization";
    public static final String ATTR_REMEMBER = "remember_me";
    public static final String ATTR_LINK_USER = "link_user";
    public static final String ATTR_LINK_MODE = "link_mode";

    private final DefaultOAuth2AuthorizationRequestResolver plain;
    private final DefaultOAuth2AuthorizationRequestResolver pkce;
    private final JwtProvider jwtProvider;

    public SocialAuthorizationRequestResolver(ClientRegistrationRepository repository, JwtProvider jwtProvider) {
        this.plain = new DefaultOAuth2AuthorizationRequestResolver(repository, BASE_URI);
        this.pkce = new DefaultOAuth2AuthorizationRequestResolver(repository, BASE_URI);
        this.pkce.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        this.jwtProvider = jwtProvider;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = BASE_URI + "/";
        if (!uri.startsWith(prefix)) {
            return null;
        }
        return resolve(request, uri.substring(prefix.length()));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        DefaultOAuth2AuthorizationRequestResolver delegate = "google".equals(registrationId) ? pkce : plain;
        OAuth2AuthorizationRequest base = delegate.resolve(request, registrationId);
        if (base == null) {
            return null;
        }
        boolean remember = "1".equals(request.getParameter("remember"))
                || "true".equals(request.getParameter("remember"));
        boolean link = "link".equals(request.getParameter("mode"));
        return OAuth2AuthorizationRequest.from(base)
                .attributes(attrs -> {
                    attrs.put(ATTR_REMEMBER, String.valueOf(remember));
                    if (link) {
                        attrs.put(ATTR_LINK_MODE, "true");
                        jwtProvider.parse(AuthCookies.read(request, AuthCookies.ACCESS))
                                .ifPresent(id -> attrs.put(ATTR_LINK_USER, String.valueOf(id)));
                    }
                })
                .build();
    }
}
