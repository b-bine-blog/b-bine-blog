package com.mainblog.common.masking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 이메일 가리기 (T065, FR-036, 4.4). */
class EmailMaskerTest {

    @Test
    void 앞_2글자만_보인다() {
        assertThat(EmailMasker.mask("abcdef@gmail.com")).isEqualTo("ab***@gmail.com");
        assertThat(EmailMasker.mask("abc@x.com")).isEqualTo("ab***@x.com");
    }

    @Test
    void 앞이_2글자_이하면_첫_글자만_보인다() {
        assertThat(EmailMasker.mask("ab@x.com")).isEqualTo("a***@x.com");
        assertThat(EmailMasker.mask("a@x.com")).isEqualTo("a***@x.com");
    }

    @Test
    void 원문의_가린_부분은_남지_않는다() {
        String masked = EmailMasker.mask("secretname@corp.com");
        assertThat(masked).doesNotContain("cretname").isEqualTo("se***@corp.com");
    }
}
