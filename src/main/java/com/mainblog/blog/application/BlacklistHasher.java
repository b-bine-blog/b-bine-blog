package com.mainblog.blog.application;

import com.mainblog.common.config.BlacklistProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 블랙리스트 해시 (T012, FR-057, 4.6, research R-09).
 * 정규화한 값을 HMAC-SHA256(blacklist.hash-secret)으로 만든 16진수 64자. 키 없이는 전화번호를 거꾸로 찾을 수 없다.
 */
@Component
public class BlacklistHasher {

    private static final HexFormat HEX = HexFormat.of();

    private final SecretKeySpec key;

    public BlacklistHasher(BlacklistProperties properties) {
        this.key = new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String name(String name) {
        return hmac("name:" + (name == null ? "" : name.strip().replaceAll("\\s+", " ")));
    }

    public String email(String email) {
        return hmac("email:" + (email == null ? "" : email.strip().toLowerCase(Locale.ROOT)));
    }

    public String phone(String phone) {
        return hmac("phone:" + (phone == null ? "" : phone.replaceAll("\\D", "")));
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HEX.formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256을 쓸 수 없습니다", e);
        }
    }
}
