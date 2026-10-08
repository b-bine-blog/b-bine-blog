package com.mainblog.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainblog.auth.application.TokenService;
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
            + "frame-src https://challenges.cloudflare.com";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint entryPoint,
                                                   JwtProvider jwtProvider, UserRepository userRepository,
                                                   TokenService tokenService) throws Exception {
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
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(c -> {})
                        .contentSecurityPolicy(c -> c.policyDirectives(CSP))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ActivityTrackingFilter(tokenService), JwtAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/members/nickname-availability").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public-config").permitAll()
                        .requestMatchers("/api/members/me/**").authenticated()
                        // 비회원도 보는 블로그 API(FR-014, FR-015, FR-025, FR-026). 볼 수 있는지는 BlogAccessPolicy가 본다.
                        .requestMatchers(HttpMethod.GET, "/api/blogs", "/api/blogs/search", "/api/blogs/address/*",
                                "/api/blogs/*/share-link").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/blogs/share-access").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // 정적 화면(D-66). 화면이 부르는 API에서 권한을 확인한다.
                        .anyRequest().permitAll());
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
