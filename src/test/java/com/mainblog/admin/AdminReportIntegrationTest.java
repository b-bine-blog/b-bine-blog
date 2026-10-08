package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 관리자 신고 처리 (005 T033, US4-1~8, FR-030~FR-034, D-93, D-102 #15·#16, D-103 #18). */
class AdminReportIntegrationTest extends AdminTestBase {

    private long report(Member reporter, String type, long targetId, Long blogId) throws Exception {
        Res r = postAs(reporter, "/api/reports", reportJson(type, targetId, blogId, "SPAM", null));
        if (r.status() != 201) {
            throw new AssertionError("신고 실패: " + r.status() + " " + r.body());
        }
        return r.json().get("id").asLong();
    }

    private Res resolve(Member admin, long reportId, String result, String reason) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("result", result);
        body.put("reason", reason);
        return postAs(admin, "/api/admin/reports/" + reportId + "/resolution", json(body));
    }

    private static List<Long> ids(JsonNode items) {
        List<Long> out = new ArrayList<>();
        items.forEach(i -> out.add(i.get("id").asLong()));
        return out;
    }

    @Test
    void 신고함에는_관리자_접수_4종만_오래된_순으로_나오고_블로그장_신고함에는_없다() throws Exception {
        Member admin = admin("관리자1");
        BlogFixture b = blog("report-box", "PUBLIC");
        Member member = memberOf(b.blogId(), "멤버1");
        Member reporter = member("신고자1");
        long ownerPost = writePost(b.owner(), b.blogId(), "블로그장 글", "본문");
        long memberPost = writePost(member, b.blogId(), "멤버 글", "본문");

        long r1 = report(reporter, "BLOG", b.blogId(), null);
        clock.advance(Duration.ofMinutes(1));
        long r2 = report(reporter, "USER", b.owner().id(), b.blogId());
        clock.advance(Duration.ofMinutes(1));
        long r3 = report(reporter, "POST", ownerPost, null);
        clock.advance(Duration.ofMinutes(1));
        long r4 = report(reporter, "USER", member.id(), null);
        long memberReport = report(reporter, "POST", memberPost, null);
        admin = relogin(admin);

        JsonNode page = getAs(admin, "/api/admin/reports").json();
        assertThat(ids(page.get("items"))).containsExactly(r1, r2, r3, r4);
        assertThat(ids(page.get("items"))).doesNotContain(memberReport);
        assertThat(page.get("items").get(3).get("blog").isNull()).isTrue();
        assertThat(page.get("items").get(0).get("reason").asText()).isEqualTo("SPAM");
        Member owner = relogin(b.owner());
        assertThat(ids(getAs(owner, "/api/blogs/" + b.blogId() + "/reports").json().get("items")))
                .containsExactly(memberReport);
        assertThat(getAs(admin, "/api/admin/reports?status=WRONG").status()).isEqualTo(400);

        resolve(admin, r1, "NONE", null);
        clock.advance(Duration.ofMinutes(1));
        resolve(relogin(admin), r3, "NONE", null);
        assertThat(ids(getAs(relogin(admin), "/api/admin/reports?status=DONE").json().get("items")))
                .containsExactly(r3, r1);
        assertThat(getAs(relogin(admin), "/api/admin/reports/" + memberReport).status()).isEqualTo(404);
    }

    @Test
    void 상세에_당시_내용과_신고자_닉네임이_있고_탈퇴_신고자는_탈퇴한_회원이다() throws Exception {
        Member admin = admin("관리자2");
        BlogFixture b = blog("report-detail", "PUBLIC");
        Member reporter = member("신고자2");
        long id = report(reporter, "BLOG", b.blogId(), null);

        JsonNode d = getAs(admin, "/api/admin/reports/" + id).json();
        assertThat(d.get("targetSnapshot").asText()).isNotBlank();
        assertThat(d.get("reporter").get("nickname").asText()).isEqualTo("신고자2");
        assertThat(d.get("targetOwner").get("id").asLong()).isEqualTo(b.owner().id());
        assertThat(d.get("targetOwnerIsCurrentOwner").asBoolean()).isTrue();
        assertThat(d.get("blog").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(d.toString()).doesNotContain(reporter.email());

        withdraw(reporter.id());
        JsonNode after = getAs(admin, "/api/admin/reports/" + id).json();
        assertThat(after.get("reporter").get("nickname").asText()).isEqualTo("탈퇴한 회원");
        assertThat(after.get("reporter").get("withdrawn").asBoolean()).isTrue();
    }

    @Test
    void 결과마다_같은_조치를_하고_기록과_신고자_알림이_남는다() throws Exception {
        Member admin = admin("관리자3");
        BlogFixture b = blog("report-act", "PUBLIC");
        Member reporter = member("신고자3");
        long none = insertAdminReport(b.blogId(), reporter.id(), "BLOG", b.blogId());
        long w1 = insertAdminReport(b.blogId(), reporter.id(), "USER", b.owner().id());
        long w2 = insertAdminReport(b.blogId(), reporter.id(), "USER", b.owner().id());
        long w3 = insertAdminReport(b.blogId(), reporter.id(), "USER", b.owner().id());

        Res n = resolve(admin, none, "NONE", null);
        assertThat(n.status()).isEqualTo(200);
        assertThat(n.json().get("status").asText()).isEqualTo("DONE");
        assertThat(n.json().get("result").asText()).isEqualTo("NONE");
        assertThat(n.json().get("handledBy").get("id").asLong()).isEqualTo(admin.id());
        assertThat(count("SELECT COUNT(*) FROM owner_warnings")).isZero();

        assertThat(resolve(admin, w1, "OWNER_WARNING", " ").status()).isEqualTo(400);
        assertThat(resolve(admin, w1, "WARNING", "x").status()).isEqualTo(400);
        assertThat(resolve(admin, w1, "OWNER_WARNING", "신고 확인").status()).isEqualTo(200);
        assertThat(count("SELECT COUNT(*) FROM owner_warnings WHERE report_id = ?", w1)).isEqualTo(1);
        resolve(admin, w2, "OWNER_WARNING", "두 번째");
        assertThat(count("SELECT COUNT(*) FROM owner_revocations")).isZero();
        resolve(admin, w3, "OWNER_WARNING", "세 번째");
        assertThat(count("SELECT COUNT(*) FROM owner_revocations WHERE blog_id = ?", b.blogId())).isEqualTo(1);
        assertThat(role(b.blogId(), b.owner().id())).isEqualTo("MEMBER");

        assertThat(actions("REPORT_HANDLE")).hasSize(4);
        assertThat(((Number) actions("OWNER_WARNING").get(0).get("report_id")).longValue()).isEqualTo(w1);
        assertThat(((Number) actions("OWNER_KICK").get(0).get("report_id")).longValue()).isEqualTo(w3);
        assertThat(notificationsOf(reporter.id()).stream().filter("REPORT_RESOLVED"::equals).count()).isEqualTo(4);
        assertThat(notificationContents(reporter.id(), "REPORT_RESOLVED").get(1)).contains("블로그장 경고");
    }

    @Test
    void 강퇴와_블로그_폐쇄_결과() throws Exception {
        Member admin = admin("관리자4");
        BlogFixture kickBlog = blog("report-kick", "PUBLIC");
        Member s = member("부장4");
        addMember(kickBlog.blogId(), s.id(), "MANAGER");
        Member reporter = member("신고자4");
        long post = writePost(kickBlog.owner(), kickBlog.blogId(), "문제 글", "본문");
        long kick = insertAdminReport(kickBlog.blogId(), reporter.id(), "POST", post);
        Res k = resolve(admin, kick, "OWNER_KICK", null);
        assertThat(k.status()).isEqualTo(200);
        assertThat(k.json().get("result").asText()).isEqualTo("OWNER_KICK");
        assertThat(role(kickBlog.blogId(), s.id())).isEqualTo("OWNER");
        assertThat(((Number) actions("AUTO_DELEGATE").get(0).get("report_id")).longValue()).isEqualTo(kick);

        BlogFixture closeBlog = blog("report-close", "PUBLIC");
        long close = insertAdminReport(closeBlog.blogId(), reporter.id(), "BLOG", closeBlog.blogId());
        assertThat(resolve(admin, close, "BLOG_CLOSE", null).status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT status FROM blogs WHERE id = ?", String.class, closeBlog.blogId()))
                .isEqualTo("CLOSING");
        assertThat(((Number) actions("BLOG_FORCE_CLOSE").get(0).get("report_id")).longValue()).isEqualTo(close);

        // 이미 폐쇄 예정이면 사유만 바꾼다
        long again = insertAdminReport(closeBlog.blogId(), reporter.id(), "BLOG", closeBlog.blogId());
        assertThat(resolve(admin, again, "BLOG_CLOSE", null).status()).isEqualTo(200);
        assertThat(actions("BLOG_FORCE_CLOSE")).hasSize(2);
        assertThat(actions("BLOG_FORCE_CLOSE").get(1).get("memo").toString()).contains("기존 폐쇄 예정 유지");
    }

    @Test
    void 블로그장이_바뀌었으면_409이고_블로그_없는_신고는_문제_없음만_된다() throws Exception {
        Member admin = admin("관리자5");
        BlogFixture b = blog("report-changed", "PUBLIC");
        Member s = member("부장5");
        addMember(b.blogId(), s.id(), "MANAGER");
        Member reporter = member("신고자5");
        long post = writePost(b.owner(), b.blogId(), "옛 블로그장 글", "본문");
        long r = insertAdminReport(b.blogId(), reporter.id(), "POST", post);
        postAs(admin, "/api/admin/blogs/" + b.blogId() + "/owner-revocation", "{\"ownerId\":" + b.owner().id() + "}");

        assertThat(getAs(admin, "/api/admin/reports/" + r).json().get("targetOwnerIsCurrentOwner").asBoolean()).isFalse();
        Res changed = resolve(admin, r, "OWNER_WARNING", "늦은 경고");
        assertThat(changed.status()).isEqualTo(409);
        assertThat(changed.code()).isEqualTo("OWNER_CHANGED");
        assertThat(resolve(admin, r, "BLOG_CLOSE", null).code()).isEqualTo("OWNER_CHANGED");
        assertThat(resolve(admin, r, "NONE", null).status()).isEqualTo(200);

        Member target = member("메인회원5");
        long main = insertAdminReport(null, reporter.id(), "USER", target.id());
        assertThat(resolve(admin, main, "OWNER_WARNING", "x").status()).isEqualTo(400);
        assertThat(resolve(admin, main, "BLOG_CLOSE", null).status()).isEqualTo(400);
        assertThat(resolve(admin, main, "NONE", null).status()).isEqualTo(200);
        assertThat(getAs(admin, "/api/admin/reports/" + main).json().get("blog").isNull()).isTrue();
    }

    @Test
    void 한_번만_처리되고_동시_두_처리는_하나만_성공한다() throws Exception {
        Member a1 = admin("관리자6");
        Member a2 = admin("관리자7");
        BlogFixture b = blog("report-once", "PUBLIC");
        Member reporter = member("신고자6");
        long r = insertAdminReport(b.blogId(), reporter.id(), "BLOG", b.blogId());
        assertThat(resolve(a1, r, "NONE", null).status()).isEqualTo(200);
        Res again = resolve(a2, r, "OWNER_KICK", null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("REPORT_ALREADY_HANDLED");

        long r2 = insertAdminReport(b.blogId(), reporter.id(), "BLOG", b.blogId());
        List<Callable<Integer>> jobs = List.of(
                () -> resolve(a1, r2, "OWNER_WARNING", "동시 1").status(),
                () -> resolve(a2, r2, "OWNER_WARNING", "동시 2").status());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            for (Future<Integer> f : pool.invokeAll(jobs)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(count("SELECT COUNT(*) FROM owner_warnings WHERE report_id = ?", r2)).isEqualTo(1);
        assertThat(actions("REPORT_HANDLE")).hasSize(2);
    }

    @Test
    void 신고자가_결과_알림을_껐으면_알림은_없고_처리와_기록은_남는다_신고가_쌓여도_자동_조치는_없다() throws Exception {
        Member admin = admin("관리자8");
        BlogFixture b = blog("report-quiet", "PUBLIC");
        Member reporter = member("신고자8");
        setNotification(reporter.id(), "report_result", false);
        long r = insertAdminReport(b.blogId(), reporter.id(), "BLOG", b.blogId());
        assertThat(resolve(admin, r, "NONE", null).status()).isEqualTo(200);
        assertThat(notificationsOf(reporter.id())).doesNotContain("REPORT_RESOLVED");
        assertThat(actions("REPORT_HANDLE")).hasSize(1);

        BlogFixture many = blog("report-many", "PUBLIC");
        for (int i = 0; i < 5; i++) {
            Member m = member("다수신고" + i);
            report(m, "BLOG", many.blogId(), null);
        }
        assertThat(count("SELECT COUNT(*) FROM reports WHERE blog_id = ? AND route = 'ADMIN'", many.blogId()))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT status FROM blogs WHERE id = ?", String.class, many.blogId()))
                .isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM owner_warnings WHERE blog_id = ?", many.blogId())).isZero();
        assertThat(role(many.blogId(), many.owner().id())).isEqualTo("OWNER");
    }
}
