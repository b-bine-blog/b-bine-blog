package com.mainblog.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainblog.auth.application.TokenService;
import com.mainblog.auth.oauth.CookieAuthorizationRequestRepository;
import com.mainblog.auth.oauth.NoStoreAuthorizedClientRepository;
import com.mainblog.auth.oauth.SocialAuthorizationRequestResolver;
import com.mainblog.auth.oauth.SocialClients;
import com.mainblog.auth.oauth.SocialLoginHandlers;
import com.mainblog.auth.oauth.SocialLoginService;
import com.mainblog.auth.security.AuthCookies;
import com.mainblog.common.ratelimit.ClientIpResolver;
import com.mainblog.common.ratelimit.RateLimiter;
import java.time.Clock;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import com.mainblog.auth.security.ActivityTrackingFilter;
import com.mainblog.auth.security.JwtAuthenticationFilter;
import com.mainblog.auth.security.JwtProvider;
import com.mainblog.member.domain.UserRepository;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * 보안 설정 (T009).
 * CSRF는 쿠키(XSRF-TOKEN) + 헤더(X-XSRF-TOKEN) 방식(SEC-10, R-12), 세션 없음(SCL-01),
 * 보안 헤더(SEC-12), bcrypt(SEC-01, D-81, R-09).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String CSP = "default-src 'self'; "
            + "script-src 'self' https://challenges.cloudflare.com; "
            // 로그인·회원가입 창은 같은 사이트 화면을 iframe으로 띄운다(D-118). 다른 사이트는 우리 화면을 품을 수 없다
            + "frame-src 'self' https://challenges.cloudflare.com; "
            + "frame-ancestors 'self'; "
            // 글 본문의 바깥 이미지(https)와 에디터 CSS의 data: 아이콘(004 R-01, R-02)
            + "img-src 'self' data: https:; "
            // Toast UI 에디터가 style 속성으로 팝업 위치를 정한다. 스크립트는 그대로 막는다(D-101)
            + "style-src 'self' 'unsafe-inline'";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint entryPoint,
                                                   AccessDeniedHandler accessDeniedHandler, JwtProvider jwtProvider,
                                                   UserRepository userRepository,
                                                   TokenService tokenService, SocialClients socialClients,
                                                   SocialLoginService socialLoginService, AuthCookies authCookies,
                                                   RateLimiter rateLimiter, ClientIpResolver ipResolver,
                                                   AuthProperties authProperties, OAuthProperties oauthProperties,
                                                   ObjectMapper objectMapper, Clock clock) throws Exception {
        // api.js가 쿠키 값을 그대로 헤더에 담으므로 XOR 마스킹 없는 처리기를 쓴다.
        // 요청 속성 이름을 비워 토큰을 매 요청 바로 읽게 해, 모든 응답에 XSRF-TOKEN 쿠키가 실린다.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .frameOptions(f -> f.sameOrigin()) // 같은 사이트만 iframe으로 품을 수 있다(D-118, 클릭재킹 방지는 유지)
                        .contentTypeOptions(c -> {})
                        .contentSecurityPolicy(c -> c.policyDirectives(CSP))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ActivityTrackingFilter(tokenService), JwtAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/social/**").permitAll() // 소셜 가입·연결 대기(008). 대기 토큰으로 확인한다
                        .requestMatchers(HttpMethod.GET, "/api/members/nickname-availability").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public-config").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/site-rules").permitAll() // 운영 규칙 화면(D-114)
                        .requestMatchers(HttpMethod.POST, "/api/inquiries").permitAll() // 비회원 시스템 문의(ADM-08, D-112)
                        .requestMatchers("/api/members/me/**").authenticated()
                        // 비회원도 보는 블로그 API(FR-014, FR-015, FR-025, FR-026). 볼 수 있는지는 BlogAccessPolicy가 본다.
                        .requestMatchers(HttpMethod.GET, "/api/blogs", "/api/blogs/search", "/api/blogs/address/*",
                                "/api/blogs/*/share-link").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/blogs/share-access").permitAll()
                        // 비회원도 보는 게시판 API(004 FR-005, FR-017, FR-023, FR-052, FR-061, FR-065). 볼 수 있는지는 서비스가 본다.
                        .requestMatchers(HttpMethod.GET, "/api/blogs/*/posts", "/api/blogs/*/categories",
                                "/api/posts/*", "/api/posts/*/card", "/api/posts/*/comments", "/api/tags/*/posts", "/api/search",
                                "/api/feed", "/api/notices").permitAll()
                        // 비회원도 보는 프로필·팔로워·팔로잉 목록(003 FR-005, D-102 #8·#9). 나머지 소셜·알림 API는 로그인 필요
                        .requestMatchers(HttpMethod.GET, "/api/users/*/profile", "/api/users/*/followers",
                                "/api/users/*/following").permitAll()
                        // 메인 관리자 API(005 FR-001~FR-003, SEC-11). 권한은 JwtAuthenticationFilter가 매 요청 DB의 role로 붙인다
                        .requestMatchers("/api/admin/**").hasAuthority(JwtAuthenticationFilter.ADMIN)
                        .requestMatchers("/api/**").authenticated()
                        // 정적 화면(D-66). 화면이 부르는 API에서 권한을 확인한다.
                        .anyRequest().permitAll());

        // 소셜 로그인(008, D-125). 키가 하나도 없으면 켜지 않는다(FR-014).
        if (socialClients.any()) {
            ClientRegistrationRepository registrations = socialClients.repository();
            SocialLoginHandlers handlers = new SocialLoginHandlers(socialLoginService, authCookies, rateLimiter,
                    ipResolver, authProperties);
            http.oauth2Login(o -> o
                    .clientRegistrationRepository(registrations)
                    .authorizedClientRepository(new NoStoreAuthorizedClientRepository())
                    .authorizationEndpoint(a -> a
                            .authorizationRequestResolver(
                                    new SocialAuthorizationRequestResolver(registrations, jwtProvider))
                            .authorizationRequestRepository(new CookieAuthorizationRequestRepository(
                                    objectMapper, authProperties, oauthProperties, authCookies, clock)))
                    .redirectionEndpoint(r -> r.baseUri("/login/oauth2/code/*"))
                    .successHandler(handlers)
                    .failureHandler(handlers));
        }
        return http.build();
    }

    /** 로그인이 필요한 API에 토큰 없이 오면 401 UNAUTHENTICATED (contracts의 Unauthenticated). */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, ex) -> {
            ErrorCode code = ErrorCode.UNAUTHENTICATED;
            response.setStatus(code.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, null));
        };
    }

    /**
     * 로그인했지만 관리자가 아닌 회원이 /api/admin/**을 부르면 403 ADMIN_ONLY (005 T006, contracts/admin-api.yaml AdminOnly).
     * 회원 정보는 담지 않는다(FR-003, D-28). CSRF 거부는 지금처럼 본문 없는 403이다.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();
        return (request, response, ex) -> {
            if (ex instanceof CsrfException) {
                fallback.handle(request, response, ex);
                return;
            }
            ErrorCode code = ErrorCode.ADMIN_ONLY;
            response.setStatus(code.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, null));
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
