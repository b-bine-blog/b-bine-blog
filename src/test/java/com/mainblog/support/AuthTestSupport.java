package com.mainblog.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 테스트용 요청 도우미. 상태를 바꾸는 요청에는 화면(api.js)과 같은 방식으로 XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더를 붙인다(SEC-10).
 * spring-security-test의 csrf()는 공유 컨텍스트의 토큰 저장소를 바꿔 쿠키가 사라지므로 쓰지 않는다.
 */
public final class AuthTestSupport {

    public static final String CSRF = "test-csrf-token";

    private AuthTestSupport() {
    }

    public static MockHttpServletRequestBuilder postJson(String url, String json) {
        return withCsrf(post(url)).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    public static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        return builder.cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
    }

    public static String signupJson(String email, String nickname) {
        return """
                {"email":"%s","password":"Abcd123!","name":"홍길동","nickname":"%s","phone":"010-1234-5678",
                 "agreeTerms":true,"agreePrivacy":true}""".formatted(email, nickname);
    }
}
