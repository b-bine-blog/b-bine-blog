package com.mainblog.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 비밀번호 규칙 (T024, 인수 시나리오 1-6, FR-008, D-97). */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"abcd1234", "Ab1!", "Abcdefgh1!234567", "abcdefgh!", "12345678!", "Abcd 123!", "Abcd123!한"})
    void 규칙에_맞지_않으면_거부(String password) {
        assertThat(policy.isValid(password)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd123!", "Abcdefg1!2345~", "A1!aaaaa", "pass{1}word", "x9`yyyyy"})
    void 영문_숫자_특수문자를_모두_넣은_8에서_15자는_통과(String password) {
        assertThat(policy.isValid(password)).isTrue();
    }

    @org.junit.jupiter.api.Test
    void 널은_거부() {
        assertThat(policy.isValid(null)).isFalse();
    }
}
