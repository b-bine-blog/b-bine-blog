package com.mainblog.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 소셜 설정값 (003 T002, D-68). 값과 근거는 문서 저장소 specs/003-social-notification/data-model.md "설정값".
 * 소개·신고 설명 길이는 DB 칸(VARCHAR(500))보다 크게 잡으면 앱 시작 때 실패한다.
 */
@Validated
@ConfigurationProperties("social")
public record SocialProperties(
        @Valid @NotNull RateLimit follow,
        @Valid @NotNull RateLimit subscribe,
        @Valid @NotNull ListConfig list,
        @Valid @NotNull Bio bio,
        @Valid @NotNull Report report) {

    /** 회원당 요청 제한(D-102 #4). */
    public record RateLimit(@Positive int rateLimit, @NotNull Duration rateWindow) {}

    public record ListConfig(@Positive int pageSize) {}

    public record Bio(@Positive @Max(500) int maxLength) {}

    /**
     * @param reasons        "코드=이름" 목록(D-102 #14). 순서가 화면 순서다.
     * @param detailRequired 설명이 꼭 있어야 하는 사유 코드
     */
    public record Report(@NotEmpty List<String> reasons, @NotNull List<String> detailRequired,
                         @Positive @Max(500) int detailMaxLength, @NotNull Duration repeatAfter) {

        public Report {
            if (reasons != null) {
                for (String r : reasons) {
                    int eq = r.indexOf('=');
                    if (eq <= 0 || eq == r.length() - 1 || r.substring(0, eq).strip().length() > 30) {
                        throw new IllegalArgumentException("social.report.reasons는 '코드=이름'이고 코드는 30자 이하여야 합니다: " + r);
                    }
                }
            }
        }

        /** 사유 코드 → 이름, 설정 순서대로. */
        public Map<String, String> reasonLabels() {
            Map<String, String> map = new LinkedHashMap<>();
            for (String r : reasons) {
                int eq = r.indexOf('=');
                map.put(r.substring(0, eq).strip(), r.substring(eq + 1).strip());
            }
            return map;
        }
    }
}
