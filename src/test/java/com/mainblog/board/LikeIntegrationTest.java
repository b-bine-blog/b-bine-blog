package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 좋아요 (004 T036, US5-8·9, FR-042~FR-044, SC-006, D-100 #12·#13). */
class LikeIntegrationTest extends BoardTestBase {

    @Test
    void 누르면_켜지고_다시_누르면_취소되며_알림은_처음_한_번() throws Exception {
        BlogFixture b = blog("like-toggle", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member fan = member("팬");
        String url = "/api/posts/" + post + "/like";
        Res on = putAs(fan, url, null);
        assertThat(on.json().get("liked").asBoolean()).isTrue();
        assertThat(on.json().get("likeCount").asInt()).isEqualTo(1);
        Res off = putAs(fan, url, null);
        assertThat(off.json().get("liked").asBoolean()).isFalse();
        assertThat(off.json().get("likeCount").asInt()).isZero();
        putAs(fan, url, null);
        assertThat(notificationsOf(b.owner().id())).containsExactly("POST_LIKE");
        assertThat(getAs(fan, "/api/posts/" + post).json().get("liked").asBoolean()).isTrue();
    }

    @Test
    void 동시에_10번_눌러도_행과_개수가_어긋나지_않는다() throws Exception {
        BlogFixture b = blog("like-race", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member fan = member("팬");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return putAs(fan, "/api/posts/" + post + "/like", null).status();
            }));
        }
        start.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();
        int rows = jdbc.queryForObject("SELECT COUNT(*) FROM post_likes WHERE post_id = ?", Integer.class, post);
        assertThat(rows).isLessThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT like_count FROM posts WHERE id = ?", Integer.class, post)).isEqualTo(rows);
        assertThat(notificationsOf(b.owner().id())).containsExactly("POST_LIKE");
    }

    @Test
    void 자기_글은_알림이_없고_1분에_31번째는_429() throws Exception {
        BlogFixture b = blog("like-limit", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        for (int i = 0; i < 30; i++) {
            assertThat(putAs(b.owner(), "/api/posts/" + post + "/like", null).status()).isEqualTo(200);
        }
        Res r = putAs(b.owner(), "/api/posts/" + post + "/like", null);
        assertThat(r.status()).isEqualTo(429);
        assertThat(r.code()).isEqualTo("RATE_LIMITED");
        assertThat(notificationsOf(b.owner().id())).isEmpty();
    }

    @Test
    void 관리자_403_공지_400_비회원_401() throws Exception {
        BlogFixture b = blog("like-deny", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member adm = admin("관리");
        assertThat(putAs(adm, "/api/posts/" + post + "/like", null).code()).isEqualTo("ADMIN_NOT_ALLOWED");
        long notice = postAs(adm, "/api/notices", postJson("공지", "내용", null, null)).json().get("id").asLong();
        assertThat(putAs(member("회원"), "/api/posts/" + notice + "/like", null).code()).isEqualTo("LIKE_NOT_ALLOWED");
        assertThat(putAs(null, "/api/posts/" + post + "/like", null).status()).isEqualTo(401);
    }
}
