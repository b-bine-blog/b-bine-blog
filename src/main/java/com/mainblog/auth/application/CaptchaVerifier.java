package com.mainblog.auth.application;

import com.mainblog.common.config.AuthProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 사람 확인 (T038, SEC-13, D-79, R-07). Cloudflare Turnstile siteverify로 서버에서 확인한다.
 * 키가 없거나 확인 요청이 실패하면 통과시키지 않는다.
 */
@Component
public class CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(CaptchaVerifier.class);

    private final AuthProperties.Turnstile turnstile;
    private final RestClient restClient;

    public CaptchaVerifier(AuthProperties properties, RestClient.Builder builder) {
        this.turnstile = properties.turnstile();
        this.restClient = builder.build();
    }

    public boolean verify(String token, String remoteIp) {
        if (token == null || token.isBlank() || turnstile.secretKey() == null || turnstile.secretKey().isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", turnstile.secretKey());
        form.add("response", token);
        if (remoteIp != null) {
            form.add("remoteip", remoteIp);
        }
        try {
            Map<?, ?> result = restClient.post()
                    .uri(turnstile.verifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return result != null && Boolean.TRUE.equals(result.get("success"));
        } catch (RestClientException e) {
            log.warn("사람 확인 요청 실패: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
