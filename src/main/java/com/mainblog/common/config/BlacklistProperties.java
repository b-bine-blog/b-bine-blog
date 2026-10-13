package com.mainblog.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 블랙리스트 HMAC 키 (T003, research R-09). 기본값이 없어 비어 있으면 시작에 실패한다. */
@Validated
@ConfigurationProperties("blacklist")
public record BlacklistProperties(@NotBlank String hashSecret) {}
