package com.mainblog.common.ratelimit;

import java.time.Duration;

/**
 * IP·회원별 요청 제한 (T019, SCL-01, R-06). 구현은 DB 장부(JdbcRateLimiter, D-140)라 앱이 여러 대여도 함께 센다.
 */
public interface RateLimiter {

    /** 이 창에서 요청을 하나 더 받을 수 있으면 true. */
    boolean tryAcquire(String key, int limit, Duration window);

    /** 거부됐을 때 Retry-After로 알려 줄 남은 초. */
    long retryAfterSeconds(String key, Duration window);
}
