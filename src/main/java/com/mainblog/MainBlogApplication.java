package com.mainblog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** 로그인은 JWT 필터가 맡으므로 Spring Security 기본 사용자(시작 로그에 비밀번호를 찍는다)는 만들지 않는다. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class MainBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainBlogApplication.class, args);
    }
}
