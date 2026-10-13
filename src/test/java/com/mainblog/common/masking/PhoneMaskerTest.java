package com.mainblog.common.masking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 전화번호 가리기 (T011, FR-046, 4.4). */
class PhoneMaskerTest {

    @Test
    void 가운데_4자리를_가린다() {
        assertThat(PhoneMasker.mask("01012345678")).isEqualTo("010-****-5678");
        assertThat(PhoneMasker.mask("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(PhoneMasker.mask(null)).isNull();
    }
}
