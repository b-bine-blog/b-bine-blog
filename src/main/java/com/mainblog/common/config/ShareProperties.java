package com.mainblog.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 글 공유 메뉴 (BRD-07, D-141). 카카오 JavaScript 키가 비어 있으면 카카오톡 항목을 숨긴다. */
@ConfigurationProperties("share")
public record ShareProperties(String kakaoJsKey) {

    public ShareProperties {
        kakaoJsKey = kakaoJsKey == null ? "" : kakaoJsKey.trim();
    }
}
