package com.mainblog.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 관리자·제재 설정값 (005 T002, D-68). 값과 근거는 문서 저장소 specs/005-admin-moderation/data-model.md "설정값". */
@Validated
@ConfigurationProperties("admin")
public record AdminProperties(
        @Valid @NotNull ListConfig list,
        @Valid @NotNull Member member,
        @Valid @NotNull PrivacyView privacyView,
        @Valid @NotNull Warning warning,
        @Valid @NotNull Revocation revocation,
        @Valid @NotNull Records records,
        @Valid @NotNull Stats stats) {

    /** 관리자 목록 한 페이지 개수 선택지와 기본값(research 가정 13). */
    public record ListConfig(@NotEmpty List<Integer> pageSizes, @Positive int defaultSize) {}

    public record Member(@Positive int nicknameSearchMaxLength) {}

    /** "전체 보기" 요청 제한: 관리자 한 명당 rateWindow에 rateLimit번(research R-04). */
    public record PrivacyView(@Positive int rateLimit, @NotNull Duration rateWindow) {}

    /** 블로그장 경고: window 안 경고가 revokeThreshold번이면 박탈(ADM-07, D-95), 사유 1~reasonMaxLength자(D-103 #12). */
    public record Warning(@Positive int revokeThreshold, @NotNull Duration window, @Positive int reasonMaxLength) {}

    public record Revocation(@Positive int reasonMaxLength) {}

    /** 운영 기록 보관 기간(4.5, D-60, D-103 #19). */
    public record Records(@NotNull Duration retention) {}

    /** 일별 통계 일수(D-103 #21). */
    public record Stats(@Positive int dailyDays) {}
}
