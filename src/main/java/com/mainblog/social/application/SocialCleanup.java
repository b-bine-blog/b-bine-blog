package com.mainblog.social.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원탈퇴 때 소셜 관계 정리 (003 T045, D-102 #16, research R-16). 팔로우 두 방향·블로그 구독·차단 두 방향을 바로 지운다.
 * 그 회원이 낸 신고와 받은 신고는 처리 기록으로 남긴다. 탈퇴 처리와 같은 트랜잭션에서 부른다(WithdrawalService).
 */
@Component
public class SocialCleanup {

    private final JdbcTemplate jdbc;

    public SocialCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Removed(int follows, int subscriptions, int blocks) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public Removed onWithdraw(long userId) {
        int follows = jdbc.update("DELETE FROM follows WHERE follower_id = ? OR followee_id = ?", userId, userId);
        int subs = jdbc.update("DELETE FROM blog_subscriptions WHERE user_id = ?", userId);
        int blocks = jdbc.update("DELETE FROM user_blocks WHERE blocker_id = ? OR blocked_id = ?", userId, userId);
        return new Removed(follows, subs, blocks);
    }
}
