package com.mainblog.blog.application;

import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 매일 04:00 블로그 단계 (T050, BLG-09, FR-074, FR-075, SCL-03, research R-13). DailyCleanupJob이 ShedLock 안에서 부른다.
 * 순서: 위임 만료 → D3·D1 알림 → 폐쇄 → 30일 정리 → 지운 대표 이미지 정리.
 * 단계마다(폐쇄·정리는 블로그마다) 트랜잭션을 따로 써서 하나가 실패해도 다음이 돈다. 실패는 로그로 남기고 다음 날 다시 시도된다.
 */
@Component
public class BlogDailyTasks {

    private static final Logger log = LoggerFactory.getLogger(BlogDailyTasks.class);

    private final BlogClosingService closingService;
    private final BlogPurgeService purgeService;

    public BlogDailyTasks(BlogClosingService closingService, BlogPurgeService purgeService) {
        this.closingService = closingService;
        this.purgeService = purgeService;
    }

    public Result run(LocalDateTime now) {
        int notices = step("폐쇄 알림", () -> closingService.sendDueNotices(now));
        int closed = 0;
        for (Long id : closingService.dueBlogIds(now)) {
            closed += step("폐쇄 " + id, () -> closingService.closeIfDue(id, now) ? 1 : 0);
        }
        int purged = 0;
        for (Long id : purgeService.dueBlogIds(now)) {
            purged += step("30일 정리 " + id, () -> {
                purgeService.purge(id);
                return 1;
            });
        }
        int covers = step("대표 이미지 정리", () -> purgeService.purgeDeletedCoverImages(now));
        return new Result(notices, closed, purged, covers);
    }

    private static int step(String name, Supplier<Integer> work) {
        try {
            return work.get();
        } catch (RuntimeException e) {
            log.error("04:00 블로그 단계 실패: {}", name, e);
            return 0;
        }
    }

    public record Result(int closingNotices, int closedBlogs, int purgedBlogs, int coverImages) {
        public static final Result EMPTY = new Result(0, 0, 0, 0);
    }
}
