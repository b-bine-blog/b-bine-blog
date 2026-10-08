package com.mainblog.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mainblog.common.error.InvalidInputException;
import org.junit.jupiter.api.Test;

/** 입력값 정규화 (T024, 인수 시나리오 1-8, FR-002, FR-012, FR-013, D-97). */
class MemberInputNormalizerTest {

    private final MemberInputNormalizer normalizer = new MemberInputNormalizer();

    @Test
    void 이메일은_소문자로() {
        assertThat(normalizer.email("  User@X.com ")).isEqualTo("user@x.com");
    }

    @Test
    void 전화번호는_숫자만_남기고_010_11자리만() {
        assertThat(normalizer.phone("010-1234-5678")).isEqualTo("01012345678");
        assertThat(normalizer.phone("010 1234 5678")).isEqualTo("01012345678");
        assertThatThrownBy(() -> normalizer.phone("0212345678")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> normalizer.phone("011-1234-5678")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> normalizer.phone("010-1234-567")).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void 이름은_앞뒤_공백을_빼고_2에서_20자_한글_영문_공백() {
        assertThat(normalizer.name(" 홍길동 ")).isEqualTo("홍길동");
        assertThat(normalizer.name("Gil Dong Hong")).isEqualTo("Gil Dong Hong");
        assertThatThrownBy(() -> normalizer.name("김")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> normalizer.name("홍길동1")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> normalizer.name("가".repeat(21))).isInstanceOf(InvalidInputException.class);
    }
}
