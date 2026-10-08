package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 동시 경고 (005 T028, SC-005, FR-028): 박탈·위임·폐쇄 예약은 블로그당 한 번. */
class OwnerConcurrencyTest extends AdminTestBase {

    private List<Integer> warnTogether(Member a1, Member a2, long blogId, long ownerId) throws Exception {
        String body = "{\"ownerId\":" + ownerId + ",\"reason\":\"동시 경고\"}";
        List<Callable<Integer>> jobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            jobs.add(() -> postAs(a1, "/api/admin/blogs/" + blogId + "/owner-warnings", body).status());
            jobs.add(() -> postAs(a2, "/api/admin/blogs/" + blogId + "/owner-warnings", body).status());
        }
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        try {
            List<Integer> result = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(jobs)) {
                result.add(f.get());
            }
            return result;
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void 경고_2번인_블로그장에게_두_관리자가_동시에_10번씩_경고해도_위임은_한_번() throws Exception {
        Member a1 = admin("동시관리1");
        Member a2 = admin("동시관리2");
        BlogFixture b = blog("race-warn", "PUBLIC");
        Member s = member("동시부장");
        addMember(b.blogId(), s.id(), "MANAGER");
        insertWarning(b.blogId(), b.owner().id(), a1.id(), clock.now().minusDays(2));
        insertWarning(b.blogId(), b.owner().id(), a2.id(), clock.now().minusDays(1));

        List<Integer> statuses = warnTogether(a1, a2, b.blogId(), b.owner().id());
        assertThat(statuses).filteredOn(st -> st == 201).hasSize(1);
        assertThat(statuses).filteredOn(st -> st == 409).hasSize(19);
        assertThat(count("SELECT COUNT(*) FROM owner_revocations WHERE blog_id = ?", b.blogId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM owner_warnings WHERE blog_id = ?", b.blogId())).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM owner_warnings WHERE blog_id = ? AND user_id = ?", b.blogId(), s.id()))
                .isZero();
        assertThat(role(b.blogId(), s.id())).isEqualTo("OWNER");
        assertThat(actions("AUTO_DELEGATE")).hasSize(1);
        assertThat(notificationsOf(b.owner().id()).stream().filter("OWNER_REVOKED"::equals).count()).isEqualTo(1);
    }

    @Test
    void 부블로그장이_없어도_폐쇄_예약은_한_번() throws Exception {
        Member a1 = admin("동시관리3");
        Member a2 = admin("동시관리4");
        BlogFixture b = blog("race-close", "PUBLIC");
        Member m = memberOf(b.blogId(), "동시멤버");
        insertWarning(b.blogId(), b.owner().id(), a1.id(), clock.now().minusDays(2));
        insertWarning(b.blogId(), b.owner().id(), a2.id(), clock.now().minusDays(1));

        List<Integer> statuses = warnTogether(a1, a2, b.blogId(), b.owner().id());
        assertThat(statuses).filteredOn(st -> st == 201).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM owner_revocations WHERE blog_id = ?", b.blogId())).isEqualTo(1);
        assertThat(actions("CLOSE_SCHEDULE")).hasSize(1);
        assertThat(notificationsOf(m.id()).stream().filter("BLOG_CLOSING"::equals).count()).isEqualTo(1);
    }
}
