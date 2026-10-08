package com.mainblog.member.application;

import com.mainblog.common.error.InvalidInputException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 회원 입력값 정규화와 형식 검사 (T017).
 * 이메일 소문자(FR-002), 전화번호 숫자만·010 11자리(FR-012, D-97), 이름 앞뒤 공백 제거 후 2~20자 한글·영문·공백(FR-013, D-97).
 * 형식이 맞지 않으면 400 VALIDATION_FAILED(fieldErrors 한 건)로 끝난다.
 */
@Component
public class MemberInputNormalizer {

    private static final Pattern PHONE = Pattern.compile("^010\\d{8}$");
    private static final Pattern NAME = Pattern.compile("^[가-힣a-zA-Z ]{2,20}$");

    public String email(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("email", "Required");
        }
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    public String phone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (!PHONE.matcher(digits).matches()) {
            throw new InvalidInputException("phone", "Pattern");
        }
        return digits;
    }

    public String name(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (!NAME.matcher(name).matches()) {
            throw new InvalidInputException("name", "Pattern");
        }
        return name;
    }
}
