package com.mainblog.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/** 블로그 설정값 (T003, D-68). 값과 근거는 문서 저장소 specs/002-blog-management/data-model.md "설정값". */
@Validated
@ConfigurationProperties("blog")
public record BlogProperties(
        @Valid @NotNull Limit limit,
        @Valid @NotNull Address address,
        @Valid @NotNull Length name,
        @Valid @NotNull Length description,
        @Valid @NotNull Tag tag,
        @Valid @NotNull CoverImage coverImage,
        @Valid @NotNull ListConfig list,
        @Valid @NotNull Search search,
        @Valid @NotNull Join join,
        @Valid @NotNull Appeal appeal,
        @Valid @NotNull Delegation delegation,
        @Valid @NotNull Closing closing,
        @Valid @NotNull Sanction sanction) {

    /** 공개(일부 공개 포함) 제한은 5개까지 늘릴 수 있다(FR-011, D-52). */
    public record Limit(@Name("public") @Min(1) @Max(5) int publicLimit,
                        @Name("private") @Positive int privateLimit) {}

    public record Address(@NotNull List<String> reserved) {}

    public record Length(@Positive int maxLength) {}

    public record Tag(@Positive int maxCount, @Positive int maxLength) {}

    public record CoverImage(@NotNull DataSize maxSize, @Positive long maxPixels, @NotNull Duration purgeAfter) {}

    /** 한 페이지 개수 선택지. 첫 값이 기본(FR-014). */
    public record ListConfig(@NotEmpty List<Integer> pageSizes) {

        public int defaultSize() {
            return pageSizes.get(0);
        }
    }

    public record Search(@Positive int minLength, @Positive int maxLength) {}

    public record Join(@NotNull Duration reapplyAfter) {}

    public record Appeal(@NotNull Duration retryAfter) {}

    public record Delegation(@NotNull Duration expireAfter) {}

    public record Closing(@Positive int graceDays, @Min(0) @Max(23) int hour, @NotEmpty List<Integer> noticeDays,
                          @NotNull Duration purgeAfter) {}

    public record Sanction(@NotNull Duration countWindow, @Positive int kickSuggestCount) {}
}
