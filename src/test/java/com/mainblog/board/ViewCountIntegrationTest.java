package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 조회수 (004 T046, US8, FR-066~FR-068, SC-005, D-77, D-100 #20·#21). */
class ViewCountIntegrationTest extends BoardTestBase {

    private int views(long postId) {
        return jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, postId);
    }

    @Test
    void 같은_회원은_하루_한_번_다음_날_다시_센다() throws Exception {
        BlogFixture b = blog("view-day", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member reader = member("독자");
        for (int i = 0; i < 20; i++) {
            getAs(reader, "/api/posts/" + post);
        }
        assertThat(views(post)).isEqualTo(1);
        clock.setTo(clock.now().withHour(23).withMinute(59));
        reader = relogin(reader);
        getAs(reader, "/api/posts/" + post);
        assertThat(views(post)).isEqualTo(1);
        clock.advance(Duration.ofMinutes(2));
        reader = relogin(reader);
        getAs(reader, "/api/posts/" + post);
        assertThat(views(post)).isEqualTo(2);
    }

    @Test
    void 비회원은_VID_쿠키로_구분하고_쿠키를_굽는다() throws Exception {
        BlogFixture b = blog("view-guest", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Res first = getAs(null, "/api/posts/" + post);
        String setCookie = first.raw().getHeader("Set-Cookie");
        assertThat(setCookie).contains("VID=").contains("HttpOnly").contains("SameSite=Lax").contains("Max-Age=31536000");
        String vid = setCookie.substring(4, setCookie.indexOf(';'));
        assertThat(vid).matches("^[0-9a-f]{32}$");
        Res again = call(null, get("/api/posts/" + post).cookie(new Cookie("VID", vid)));
        assertThat(again.raw().getHeader("Set-Cookie") == null
                || !again.raw().getHeaders("Set-Cookie").toString().contains("VID=")).isTrue();
        assertThat(views(post)).isEqualTo(1);
        getAs(null, "/api/posts/" + post);
        assertThat(views(post)).isEqualTo(2);
    }

    @Test
    void 작성자_본인과_봇은_세지_않는다() throws Exception {
        BlogFixture b = blog("view-bot", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        getAs(b.owner(), "/api/posts/" + post);
        call(null, get("/api/posts/" + post).header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1)"));
        call(null, get("/api/posts/" + post).header("User-Agent", "some-crawler/1.0"));
        assertThat(views(post)).isZero();
        assertThat(getAs(null, "/api/posts/" + post).status()).isEqualTo(200);
        assertThat(views(post)).isEqualTo(1);
    }

    @Test
    void 같은_IP가_1분에_61번째로_연_글은_세지_않는다() throws Exception {
        BlogFixture b = blog("view-ip", "PUBLIC");
        List<Long> posts = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            posts.add(insertPost(b.blogId(), b.owner().id(), "글" + i, "c", clock.now()));
        }
        for (long id : posts) {
            assertThat(getAs(null, "/api/posts/" + id).status()).isEqualTo(200);
        }
        assertThat(views(posts.get(59))).isEqualTo(1);
        assertThat(views(posts.get(60))).isZero();
    }

    @Test
    void 동시에_두_번_열어도_한_번() throws Exception {
        BlogFixture b = blog("view-race", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "c");
        Member reader = member("독자");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> f = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            f.add(pool.submit(() -> {
                start.await();
                return getAs(reader, "/api/posts/" + post).status();
            }));
        }
        start.countDown();
        for (Future<Integer> x : f) {
            assertThat(x.get()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(views(post)).isEqualTo(1);
    }
}
