package com.mainblog.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 사이트 주소 (004 T003). og 태그의 절대 주소를 만들 때 쓴다(research R-12). */
@Validated
@ConfigurationProperties("app")
public record AppProperties(@NotBlank String baseUrl) {

    public String absolute(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + (path.startsWith("/") ? path : "/" + path);
    }
}
