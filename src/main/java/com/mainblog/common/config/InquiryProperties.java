package com.mainblog.common.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 시스템 문의 설정값 (ADM-08, D-68, D-112). 글자 수는 system_inquiries 칸 길이(제목 30, 내용·답변 2000, 이메일 255) 이하로 둔다.
 * 문의 보내기는 회원·비회원 모두 같은 IP에서 rateWindow 동안 rateLimit건까지.
 */
@Validated
@ConfigurationProperties("inquiry")
public record InquiryProperties(
        @Positive int titleMaxLength,
        @Positive int contentMaxLength,
        @Positive int answerMaxLength,
        @Positive int rateLimit,
        @NotNull Duration rateWindow,
        @NotNull Duration retention) {

    public InquiryProperties {
        if (titleMaxLength > 30 || contentMaxLength > 2000 || answerMaxLength > 2000) {
            throw new IllegalArgumentException("inquiry 글자 수는 system_inquiries 칸 길이를 넘을 수 없습니다");
        }
    }
}
