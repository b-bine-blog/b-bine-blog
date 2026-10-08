package com.mainblog.notification.application;

import com.mainblog.common.error.InvalidInputException;
import java.util.List;

/**
 * 화면 탭과 notifications.tab 값 (003 research R-03). 팔로우·구독 탭(FOLLOW)은 FOLLOW·SUBSCRIBE를 함께 본다.
 */
public enum NotificationTab {
    ALL(List.of()),
    COMMENT(List.of("COMMENT")),
    LIKE(List.of("LIKE")),
    FOLLOW(List.of("FOLLOW", "SUBSCRIBE")),
    BLOG(List.of("BLOG")),
    OPERATION(List.of("OPERATION"));

    private final List<String> stored;

    NotificationTab(List<String> stored) {
        this.stored = stored;
    }

    /** 요청 값(null이면 ALL). 목록 밖이면 400 VALIDATION_FAILED(tab). */
    public static NotificationTab parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("tab", "Pattern");
        }
    }

    /** WHERE에 붙일 조건. 별칭 없이 notifications 컬럼을 쓴다. 값은 상수라 SQL에 바로 넣는다. */
    public String condition() {
        if (stored.isEmpty()) {
            return "";
        }
        return " AND tab IN ('" + String.join("', '", stored) + "')";
    }

    /** 저장된 tab 값 → API 탭 값. */
    public static String apiValue(String storedTab) {
        return "SUBSCRIBE".equals(storedTab) ? FOLLOW.name() : storedTab;
    }
}
