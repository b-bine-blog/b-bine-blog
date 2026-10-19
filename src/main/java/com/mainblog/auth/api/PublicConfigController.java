package com.mainblog.auth.api;

import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.config.ShareProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면이 알아야 하는 공개 설정값 (T043). 사람 확인 위젯의 site key(R-07)와 카카오 공유 키(BRD-07, D-141)만 준다. */
@RestController
public class PublicConfigController {

    private final AuthProperties properties;
    private final ShareProperties shareProperties;

    public PublicConfigController(AuthProperties properties, ShareProperties shareProperties) {
        this.properties = properties;
        this.shareProperties = shareProperties;
    }

    @GetMapping("/api/public-config")
    public PublicConfig config() {
        return new PublicConfig(properties.turnstile().siteKey(), shareProperties.kakaoJsKey());
    }

    public record PublicConfig(String turnstileSiteKey, String kakaoJsKey) {}
}
