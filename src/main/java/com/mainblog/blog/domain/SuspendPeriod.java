package com.mainblog.blog.domain;

import java.time.Duration;

/** 정지 기간 (FR-049, 3.7, D-57). 이 넷 중에서만 고른다. 영구는 기간이 없다. */
public enum SuspendPeriod {
    DAYS_3(Duration.ofDays(3)),
    DAYS_14(Duration.ofDays(14)),
    DAYS_30(Duration.ofDays(30)),
    PERMANENT(null);

    private final Duration duration;

    SuspendPeriod(Duration duration) {
        this.duration = duration;
    }

    /** 영구 정지는 null. */
    public Duration duration() {
        return duration;
    }
}
