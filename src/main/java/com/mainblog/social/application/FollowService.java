package com.mainblog.social.application;

import com.mainblog.common.config.SocialProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import com.mainblog.common.ratelimit.RateLimiter;
import com.mainblog.social.application.SocialGuard.UserRow;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 팔로우·취소 (003 T024, SOC-01, FR-006, FR-008, FR-009, FR-014, FR-039, D-90, D-102 #1·#3·#4·#11, research R-10).
 * 한 쌍에 한 건은 uk_follows_follower_id_followee_id + INSERT IGNORE가 지킨다(SC-007).
 * 새 행이 생겼을 때만 NEW_FOLLOWER를 쓰고, event_key가 같아 다시 팔로우해도 알림이 다시 생기지 않는다.
 */
@Service
public class FollowService {

    private final JdbcTemplate jdbc;
    private final SocialGuard guard;
    private final ProfileService profileService;
    private final RateLimiter rateLimiter;
    private final NotificationWriter notifications;
    private final SocialProperties props;
    private final Clock clock;

    public FollowService(JdbcTemplate jdbc, SocialGuard guard, ProfileService profileService, RateLimiter rateLimiter,
                         NotificationWriter notifications, SocialProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.profileService = profileService;
        this.rateLimiter = rateLimiter;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    public record FollowState(boolean following, int followerCount) {}

    /** 확인 순서: 요청 제한 → 관리자 → 대상 → 자기 자신 → 차단(research R-10). */
    @Transactional
    public FollowState follow(long followerId, long targetId) {
        limit(followerId);
        UserRow me = guard.requireActor(followerId);
        guard.requireTarget(targetId);
        if (followerId == targetId) {
            throw new BusinessException(ErrorCode.SELF_NOT_ALLOWED);
        }
        if (guard.blockedEitherWay(followerId, targetId)) {
            throw new BusinessException(ErrorCode.FOLLOW_NOT_ALLOWED); // 문구에 차단을 드러내지 않는다(D-102 #11)
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int added = jdbc.update("INSERT IGNORE INTO follows (follower_id, followee_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?)", followerId, targetId, now, now);
        if (added > 0) {
            notifications.write(targetId, NotificationType.NEW_FOLLOWER, "USER", followerId, followerId,
                    me.nickname() + "님이 회원님을 팔로우했습니다.", "NEW_FOLLOWER:" + followerId);
        }
        return new FollowState(true, profileService.followerCount(targetId));
    }

    /** 팔로우하지 않았어도 200. 차단이 있어도 취소는 된다. */
    @Transactional
    public FollowState unfollow(long followerId, long targetId) {
        limit(followerId);
        guard.requireTarget(targetId);
        jdbc.update("DELETE FROM follows WHERE follower_id = ? AND followee_id = ?", followerId, targetId);
        return new FollowState(false, profileService.followerCount(targetId));
    }

    private void limit(long userId) {
        String key = "follow:" + userId;
        if (!rateLimiter.tryAcquire(key, props.follow().rateLimit(), props.follow().rateWindow())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED)
                    .with("retryAfterSeconds", rateLimiter.retryAfterSeconds(key, props.follow().rateWindow()));
        }
    }
}
