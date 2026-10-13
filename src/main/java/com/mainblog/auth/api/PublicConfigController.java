package com.mainblog.auth.api;

import com.mainblog.common.config.AuthProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면이 알아야 하는 공개 설정값 (T043). 사람 확인 위젯의 site key만 준다(R-07). */
@RestController
public class PublicConfigController {

    private final AuthProperties properties;

    public PublicConfigController(AuthProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/public-config")
    public PublicConfig config() {
        return new PublicConfig(properties.turnstile().siteKey());
    }

    public record PublicConfig(String turnstileSiteKey) {}
}
