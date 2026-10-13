package com.mainblog.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 고정 창 메모리 요청 제한 (T019, R-06). 서버 한 대 기준이며 서버를 늘리면 바꾼다(SCL-01). */
@Component
public class InMemoryRateLimiter implements RateLimiter {

    private record Bucket(long windowStart, int count) {}

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long size = window.toMillis();
        Bucket updated = buckets.compute(key, (k, b) -> {
            if (b == null || now - b.windowStart() >= size) {
                return new Bucket(now, 1);
            }
            return new Bucket(b.windowStart(), b.count() + 1);
        });
        return updated.count() <= limit;
    }

    @Override
    public long retryAfterSeconds(String key, Duration window) {
        Bucket b = buckets.get(key);
        if (b == null) {
            return 0;
        }
        long remaining = b.windowStart() + window.toMillis() - clock.millis();
        return Math.max(1, (remaining + 999) / 1000);
    }

    /** 1시간 넘게 쓰이지 않은 키를 지워 메모리가 늘지 않게 한다. 설정된 창은 모두 1시간보다 짧다. */
    @Scheduled(fixedDelay = 600_000)
    public void evictStale() {
        long cutoff = clock.millis() - Duration.ofHours(1).toMillis();
        buckets.entrySet().removeIf(e -> e.getValue().windowStart() < cutoff);
    }

    int size() {
        return buckets.size();
    }
}
