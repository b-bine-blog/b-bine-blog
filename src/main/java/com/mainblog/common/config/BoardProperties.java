package com.mainblog.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/** 게시판 설정값 (004 T003, D-68). 값과 근거는 문서 저장소 specs/004-board/data-model.md "설정값". */
@Validated
@ConfigurationProperties("board")
public record BoardProperties(
        @Valid @NotNull Post post,
        @Valid @NotNull ListConfig list,
        @Valid @NotNull Category category,
        @Valid @NotNull Tag tag,
        @Valid @NotNull Image image,
        @NotNull Duration purgeAfter,
        @Valid @NotNull Comment comment,
        @Valid @NotNull Like like,
        @Valid @NotNull View view,
        @Valid @NotNull Search search,
        @Valid @NotNull Feed feed,
        @Valid @NotNull Og og) {

    public record Post(@Positive int titleMaxLength, @Positive int contentMaxLength) {}

    /** 한 페이지 개수 선택지. 첫 값이 기본(FR-017). */
    public record ListConfig(@NotEmpty List<Integer> pageSizes) {

        public int defaultSize() {
            return pageSizes.get(0);
        }
    }

    public record Category(@Positive int maxCount, @Positive int nameMaxLength) {}

    public record Tag(@Positive int maxCount) {}

    public record Image(@NotNull DataSize maxSize, @NotNull DataSize maxRequestSize, @Positive int maxPerPost,
                        @Positive long maxPixels, @NotNull Duration orphanAfter, @NotNull Duration purgeAfter) {}

    public record Comment(@Positive int maxLength, @Positive int pageSize) {}

    public record Like(@Positive int rateLimit, @NotNull Duration rateWindow) {}

    public record View(@NotNull Duration guestCookieMaxAge, @Positive int ipLimit, @NotNull Duration ipWindow,
                       @NotNull List<String> botUserAgents, @NotNull Duration retention) {}

    public record Search(@Positive int minLength, @Positive int maxLength, @Positive int recentMax,
                         @Positive int blogPreviewSize) {}

    public record Feed(@Positive int recentDays, @Positive int pageSize, @PositiveOrZero int pinnedNotices) {}

    public record Og(@Positive int summaryLength, @NotNull Duration cardMaxAge) {}
}
