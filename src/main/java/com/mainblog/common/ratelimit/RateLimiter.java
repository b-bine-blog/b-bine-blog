package com.mainblog.common.ratelimit;

import java.time.Duration;

/**
 * IP별 요청 제한 (T019, SCL-01, R-06). 1차는 메모리 구현, 이중화 때 Redis 구현으로 바꾼다(4.3).
 */
public interface RateLimiter {

    /** 이 창에서 요청을 하나 더 받을 수 있으면 true. */
    boolean tryAcquire(String key, int limit, Duration window);

    /** 거부됐을 때 Retry-After로 알려 줄 남은 초. */
    long retryAfterSeconds(String key, Duration window);
}
