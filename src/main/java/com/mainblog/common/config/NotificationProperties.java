package com.mainblog.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 알림 설정값 (003 T002, D-68, D-72, D-102 #17·#20·#27).
 * 보관 기간은 DB CHECK(retention_days IN (7, 30))와 같아야 해서 7·30 말고는 앱 시작 때 실패한다.
 */
@Validated
@ConfigurationProperties("notification")
public record NotificationProperties(
        @Positive int pageSize,
        @NotNull Duration pollInterval,
        @Positive int badgeMax,
        @Valid @NotNull Retention retention) {

    public record Retention(int defaultDays, int shortDays) {

        public Retention {
            if (defaultDays != 30 || shortDays != 7) {
                throw new IllegalArgumentException(
                        "notification.retention은 DB CHECK와 같은 30일(기본)·7일만 쓸 수 있습니다");
            }
        }

        public boolean allows(int days) {
            return days == defaultDays || days == shortDays;
        }
    }
}
