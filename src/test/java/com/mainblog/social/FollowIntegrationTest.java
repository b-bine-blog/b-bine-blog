package com.mainblog.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 회원 팔로우 (003 T022, US4-1~3·7·8, SC-007, FR-006, FR-008, FR-009, FR-014, FR-039, D-102 #1·#3·#4·#11). */
class FollowIntegrationTest extends SocialTestBase {

    private String url(Member target) {
        return "/api/users/" + target.id() + "/follow";
    }

    @Test
    void 팔로우하면_팔로워_수가_늘고_새_팔로워_알림은_다시_팔로우해도_한_번() throws Exception {
        Member a = member("팔로워");
        Member p = member("대상");
        Res r = postAs(a, url(p), null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("following").asBoolean()).isTrue();
        assertThat(r.json().get("followerCount").asInt()).isEqualTo(1);
        assertThat(follows(a.id(), p.id())).isTrue();
        assertThat(notificationTabsOf(p.id())).containsExactly("NEW_FOLLOWER:FOLLOW");
        assertThat(postAs(a, url(p), null).json().get("followerCount").asInt()).isEqualTo(1);

        Res off = deleteAs(a, url(p));
        assertThat(off.status()).isEqualTo(200);
        assertThat(off.json().get("following").asBoolean()).isFalse();
        assertThat(off.json().get("followerCount").asInt()).isZero();
        assertThat(deleteAs(a, url(p)).status()).isEqualTo(200);

        assertThat(postAs(a, url(p), null).json().get("followerCount").asInt()).isEqualTo(1);
        assertThat(notificationsOf(p.id())).containsExactly("NEW_FOLLOWER");
        String content = jdbc.queryForObject("SELECT content FROM notifications WHERE recipient_id = ?", String.class,
                p.id());
        assertThat(content).contains("팔로워").doesNotContain(a.email());
    }

    @Test
    void 동시에_10번_팔로우해도_행_하나_알림_하나() throws Exception {
        Member a = member("빠른손");
        Member p = member("인기인");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return postAs(a, url(p), null).status();
            }));
        }
        start.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follows WHERE follower_id = ? AND followee_id = ?",
                Integer.class, a.id(), p.id())).isEqualTo(1);
        assertThat(notificationsOf(p.id())).containsExactly("NEW_FOLLOWER");
    }

    @Test
    void 자기_자신_400_관리자_403_비회원_401_탈퇴_관리자_대상_404() throws Exception {
        Member a = member("나자신");
        assertThat(postAs(a, url(a), null).code()).isEqualTo("SELF_NOT_ALLOWED");
        Member adm = admin("관리");
        assertThat(postAs(adm, url(a), null).code()).isEqualTo("ADMIN_NOT_ALLOWED");
        assertThat(postAs(null, url(a), null).status()).isEqualTo(401);
        assertThat(postAs(a, url(adm), null).code()).isEqualTo("NOT_FOUND");
        Member gone = member("떠난이");
        withdraw(gone.id());
        assertThat(postAs(a, url(gone), null).code()).isEqualTo("NOT_FOUND");
        assertThat(postAs(a, "/api/users/999999/follow", null).status()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follows", Integer.class)).isZero();
    }

    @Test
    void 어느_방향이든_차단이_있으면_403이고_문구에_차단이_없다() throws Exception {
        Member a = member("에이");
        Member b = member("비");
        block(b.id(), a.id());
        Res r = postAs(a, url(b), null);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("FOLLOW_NOT_ALLOWED");
        assertThat(r.json().get("message").asText()).doesNotContain("차단");
        assertThat(postAs(b, url(a), null).code()).isEqualTo("FOLLOW_NOT_ALLOWED");
    }

    @Test
    void 일분에_31번째는_429() throws Exception {
        Member a = member("연타");
        Member p = member("과녁");
        for (int i = 0; i < 15; i++) {
            assertThat(postAs(a, url(p), null).status()).isEqualTo(200);
            assertThat(deleteAs(a, url(p)).status()).isEqualTo(200);
        }
        Res r = postAs(a, url(p), null);
        assertThat(r.status()).isEqualTo(429);
        assertThat(r.code()).isEqualTo("RATE_LIMITED");
        assertThat(r.json().get("retryAfterSeconds").asLong()).isPositive();
    }

    @Test
    void 새_팔로워_알림을_끄면_만들지_않는다() throws Exception {
        Member a = member("조용히");
        Member p = member("끈이");
        setNotification(p.id(), "new_follower", false);
        postAs(a, url(p), null);
        assertThat(follows(a.id(), p.id())).isTrue();
        assertThat(notificationsOf(p.id())).isEmpty();
    }

    @Test
    void 팔로우하면_피드_팔로우_탭에_그_회원의_글이_보인다() throws Exception {
        BlogFixture b = blog("follow-feed", "PUBLIC");
        writePost(b.owner(), b.blogId(), "팔로우한 사람 글", "내용");
        Member fan = member("구독자팬");
        assertThat(getAs(fan, "/api/feed?tab=FOLLOWING").json().get("items")).isEmpty();
        postAs(fan, "/api/users/" + b.owner().id() + "/follow", null);
        assertThat(getAs(fan, "/api/feed?tab=FOLLOWING").json().get("items").get(0).get("title").asText())
                .isEqualTo("팔로우한 사람 글");
    }
}
