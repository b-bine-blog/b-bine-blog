package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 동시 생성 (T020, FR-012, SC-002). */
class BlogCreateConcurrencyTest extends BlogTestBase {

    @Test
    void 같은_회원이_동시에_10번_만들어도_공개_3개를_넘지_않는다() throws Exception {
        Member m = member("동시");
        createBlog(m, "base-1", "PUBLIC", "FREE");
        createBlog(m, "base-2", "PUBLIC", "FREE");
        List<Callable<Integer>> jobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String address = "race-" + i;
            jobs.add(() -> postAs(m, "/api/blogs", blogJson("동시 " + address, address, "PUBLIC", "FREE")).status());
        }
        List<Integer> statuses = runAll(jobs);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(9);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM blog_members m JOIN blogs b ON b.id = m.blog_id "
                + "WHERE m.user_id = ? AND m.role = 'OWNER' AND b.visibility <> 'PRIVATE'", Integer.class, m.id());
        assertThat(count).isEqualTo(3);
    }

    @Test
    void 두_회원이_같은_주소로_동시에_만들면_하나만_성공한다() throws Exception {
        Member a = member("동주1");
        Member b = member("동주2");
        List<Callable<Integer>> jobs = List.of(
                () -> postAs(a, "/api/blogs", blogJson("가", "same-addr", "PUBLIC", "FREE")).status(),
                () -> postAs(b, "/api/blogs", blogJson("나", "same-addr", "PUBLIC", "FREE")).status());
        List<Integer> statuses = runAll(jobs);
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    private static List<Integer> runAll(List<Callable<Integer>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        try {
            List<Future<Integer>> futures = pool.invokeAll(jobs);
            List<Integer> result = new ArrayList<>();
            for (Future<Integer> f : futures) {
                result.add(f.get());
            }
            return result;
        } finally {
            pool.shutdown();
        }
    }
}
