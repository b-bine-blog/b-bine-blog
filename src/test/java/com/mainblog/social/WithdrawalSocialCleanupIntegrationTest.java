package com.mainblog.social;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 회원탈퇴 때 소셜 관계 정리 (003 T045, D-102 #16, research R-16). 관계는 바로 사라지고 신고는 남는다. */
class WithdrawalSocialCleanupIntegrationTest extends SocialTestBase {

    @Test
    void 탈퇴하면_팔로우_구독_차단이_바로_사라지고_신고는_남는다() throws Exception {
        BlogFixture b = blog("wd-social", "PUBLIC");
        Member leaver = member("떠날이");
        Member other = member("남을이");
        Member third = member("셋째");
        follow(leaver.id(), other.id());
        follow(other.id(), leaver.id());
        follow(other.id(), third.id());
        subscribe(b.blogId(), leaver.id());
        subscribe(b.blogId(), other.id());
        block(leaver.id(), third.id());
        block(third.id(), leaver.id());
        block(other.id(), third.id());
        long byLeaver = postAs(leaver, "/api/reports", reportJson("USER", other.id(), null, "SPAM", null)).json()
                .get("id").asLong();
        long aboutLeaver = postAs(other, "/api/reports", reportJson("USER", leaver.id(), null, "SPAM", null)).json()
                .get("id").asLong();

        Res r = postAs(leaver, "/api/members/me/withdrawal", json(java.util.Map.of("password", PW)));
        assertThat(r.status()).isIn(200, 204);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follows WHERE follower_id = ? OR followee_id = ?",
                Integer.class, leaver.id(), leaver.id())).isZero();
        assertThat(subscribed(b.blogId(), leaver.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_blocks WHERE blocker_id = ? OR blocked_id = ?",
                Integer.class, leaver.id(), leaver.id())).isZero();
        // 다른 회원끼리의 관계는 그대로
        assertThat(follows(other.id(), third.id())).isTrue();
        assertThat(subscribed(b.blogId(), other.id())).isTrue();
        assertThat(blocks(other.id(), third.id())).isTrue();
        // 신고는 처리 기록으로 남는다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE id IN (?, ?)", Integer.class, byLeaver,
                aboutLeaver)).isEqualTo(2);
        assertThat(getAs(null, "/api/users/" + other.id() + "/followers").json().get("totalElements").asLong())
                .isZero();
    }
}
