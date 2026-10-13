package com.mainblog.auth.security;

import com.mainblog.common.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 인증번호와 무작위 토큰 (T014).
 * 6자리 인증번호는 HMAC-SHA256(CODE_HASH_SECRET)으로 해시해 저장하고(R-08, SEC-05),
 * Refresh Token·임시 토큰은 32바이트 무작위 값을 SHA-256 해시로 저장한다(R-03).
 */
@Component
public class CodeHasher {

    private static final HexFormat HEX = HexFormat.of();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec hmacKey;

    public CodeHasher(AuthProperties properties) {
        this.hmacKey = new SecretKeySpec(properties.codeHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** 000000~999999 중 하나. 앞자리 0도 6자리로 맞춘다. */
    public String newSixDigitCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    public String hashCode(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HEX.formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256을 쓸 수 없습니다", e);
        }
    }

    /** 입력한 인증번호가 저장된 해시와 같은지 상수 시간으로 비교한다. */
    public boolean matchesCode(String code, String storedHash) {
        if (code == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hashCode(code).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    /** 32바이트 무작위 값(Base64URL, 패딩 없음). 쿠키·링크에 그대로 쓴다. */
    public String newRandomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return BASE64URL.encodeToString(bytes);
    }

    public String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }
}
