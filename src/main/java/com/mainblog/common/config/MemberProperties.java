package com.mainblog.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/** 회원 설정값 (T006, D-68). 탈퇴 보관 30일(4.5), 프로필 사진 3MB(6.3), 닉네임 예약어(FR-011, D-97). */
@Validated
@ConfigurationProperties("member")
public record MemberProperties(
        @Valid @NotNull Withdrawal withdrawal,
        @Valid @NotNull ProfileImage profileImage,
        @Valid @NotNull Nickname nickname) {

    public record Withdrawal(@NotNull Duration retention) {}

    /** 파일 3MB(6.3), 가로×세로 한도, 지운 사진 파일을 남겨 두는 기간(images: 삭제 30일 뒤 완전 삭제). */
    public record ProfileImage(@NotNull DataSize maxSize, @Positive long maxPixels, @NotNull Duration purgeAfter) {}

    public record Nickname(@NotNull List<String> reserved) {}
}
