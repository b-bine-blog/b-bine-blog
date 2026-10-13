package com.mainblog.member.application;

import org.springframework.stereotype.Component;

/**
 * 비밀번호 규칙 (T015, FR-008, SEC-02, D-97).
 * 8~15자, 영문·숫자·특수문자 각 1자 이상. 특수문자는 키보드 영문 특수문자 32종(ASCII 33~47, 58~64, 91~96, 123~126),
 * 공백과 그 밖의 문자는 쓸 수 없다. 대소문자는 구분한다.
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 15;

    public boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }
        boolean letter = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                letter = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else if (isSpecial(c)) {
                special = true;
            } else {
                return false;
            }
        }
        return letter && digit && special;
    }

    static boolean isSpecial(char c) {
        return (c >= 33 && c <= 47) || (c >= 58 && c <= 64) || (c >= 91 && c <= 96) || (c >= 123 && c <= 126);
    }
}
