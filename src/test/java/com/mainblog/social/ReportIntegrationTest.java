package com.mainblog.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * 신고 접수 (003 T033, US6-1~4·8~10, SC-008, SC-009, SOC-06, FR-022~FR-025, FR-027, D-93, D-102 #1·#14·#15).
 * 접수처는 신고하는 순간의 역할로 정한다. 같은 대상은 14일에 한 번.
 */
class ReportIntegrationTest extends SocialTestBase {

    private Res report(Member who, String type, long targetId, Long blogId) throws Exception {
        return report(who, type, targetId, blogId, "SPAM", null);
    }

    private Res report(Member who, String type, long targetId, Long blogId, String reason, String detail)
            throws Exception {
        return postAs(who, "/api/reports", reportJson(type, targetId, blogId, reason, detail));
    }

    private long comment(Member who, long postId, String content) throws Exception {
        return postAs(who, "/api/posts/" + postId + "/comments", json(Map.of("content", content))).json().get("id")
                .asLong();
    }

    private String routeOf(long reportId) {
        return jdbc.queryForObject("SELECT route FROM reports WHERE id = ?", String.class, reportId);
    }

    @Test
    void 멤버_글은_블로그가_받고_블로그장과_멤버_관리_부블로그장에게_신고자_없이_보인다() throws Exception {
        BlogFixture b = blog("rep-member", "PUBLIC");
        Member writer = memberOf(b.blogId(), "작성자");
        Member manager = memberOf(b.blogId(), "부장");
        setManagerPerms(b.blogId(), manager.id(), false, true, false);
        Member plain = memberOf(b.blogId(), "그냥멤버");
        long post = writePost(writer, b.blogId(), "글", "내용");
        Member reporter = member("신고자");

        Res r = report(reporter, "POST", post, b.blogId());
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().get("route").asText()).isEqualTo("BLOG");
        long id = r.json().get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT blog_id FROM reports WHERE id = ?", Long.class, id))
                .isEqualTo(b.blogId());

        for (Member viewer : List.of(b.owner(), manager)) {
            Res list = getAs(viewer, "/api/blogs/" + b.blogId() + "/reports");
            assertThat(list.json().get("totalCount").asInt()).isEqualTo(1);
            assertThat(list.body()).doesNotContain("reporterId").doesNotContain("신고자");
        }
        assertThat(getAs(plain, "/api/blogs/" + b.blogId() + "/reports").status()).isEqualTo(403);
        // 신고당한 회원에게는 알리지 않는다(US6-10)
        assertThat(notificationCount(writer.id())).isZero();
    }

    @Test
    void 블로그장이_쓴_글과_댓글_블로그_자체는_관리자가_받는다() throws Exception {
        BlogFixture b = blog("rep-owner", "PUBLIC");
        long ownerPost = writePost(b.owner(), b.blogId(), "장 글", "내용");
        long ownerComment = comment(b.owner(), ownerPost, "장 댓글");
        Member reporter = member("신고자");

        assertThat(report(reporter, "POST", ownerPost, b.blogId()).json().get("route").asText()).isEqualTo("ADMIN");
        assertThat(report(reporter, "COMMENT", ownerComment, b.blogId()).json().get("route").asText())
                .isEqualTo("ADMIN");
        Res blogReport = report(reporter, "BLOG", b.blogId(), null);
        assertThat(blogReport.json().get("route").asText()).isEqualTo("ADMIN");
        // 블로그 신고 목록(002)에는 ADMIN 접수가 보이지 않는다
        assertThat(getAs(b.owner(), "/api/blogs/" + b.blogId() + "/reports").json().get("totalCount").asInt())
                .isZero();
        assertThat(notificationCount(b.owner().id())).isZero();
    }

    @Test
    void 블로그_안_회원_신고는_멤버면_블로그_블로그장이면_관리자_비멤버는_404() throws Exception {
        BlogFixture b = blog("rep-user", "PUBLIC");
        Member m = memberOf(b.blogId(), "멤버");
        Member outsider = member("밖");
        Member reporter = member("신고자");

        assertThat(report(reporter, "USER", m.id(), b.blogId()).json().get("route").asText()).isEqualTo("BLOG");
        assertThat(report(reporter, "USER", b.owner().id(), b.blogId()).json().get("route").asText())
                .isEqualTo("ADMIN");
        Res r = report(reporter, "USER", outsider.id(), b.blogId());
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void 메인_프로필_회원_신고는_관리자가_받고_블로그가_없다() throws Exception {
        Member target = member("대상");
        Member reporter = member("신고자");
        Res r = report(reporter, "USER", target.id(), null);
        assertThat(r.status()).isEqualTo(201);
        long id = r.json().get("id").asLong();
        assertThat(routeOf(id)).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT blog_id FROM reports WHERE id = ?", Long.class, id)).isNull();
        assertThat(report(reporter, "USER", 999_999, null).status()).isEqualTo(404);
    }

    @Test
    void 같은_대상은_14일에_한_번이고_다른_대상은_따로_센다() throws Exception {
        Member a = member("갑");
        Member b = member("을");
        Member reporter = member("신고자");
        assertThat(report(reporter, "USER", a.id(), null).status()).isEqualTo(201);

        clock.advance(Duration.ofDays(9));
        reporter = relogin(reporter);
        Res again = report(reporter, "USER", a.id(), null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("REPORT_TOO_SOON");
        assertThat(again.json().get("reportableAt").asText()).startsWith(BASE_TIME.plusDays(14).toLocalDate().toString());
        assertThat(report(reporter, "USER", b.id(), null).status()).isEqualTo(201);
        // 다른 회원이 같은 대상을 신고하는 것은 따로
        assertThat(report(member("다른이"), "USER", a.id(), null).status()).isEqualTo(201);

        clock.advance(Duration.ofDays(5));
        reporter = relogin(reporter);
        assertThat(report(reporter, "USER", a.id(), null).status()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE reporter_id = ? AND target_id = ?",
                Integer.class, reporter.id(), a.id())).isEqualTo(2);
    }

    @Test
    void 동시에_두_번_눌러도_한_건만_들어간다() throws Exception {
        Member target = member("대상");
        Member reporter = member("신고자");
        String body = reportJson("USER", target.id(), null, "SPAM", null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return postAs(reporter, "/api/reports", body).status();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports", Integer.class)).isEqualTo(1);
    }

    @Test
    void 사유와_설명을_확인한다() throws Exception {
        Member target = member("대상");
        Member reporter = member("신고자");

        Res bad = report(reporter, "USER", target.id(), null, "NOPE", null);
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.json().get("fieldErrors").get(0).get("field").asText()).isEqualTo("reason");
        Res noDetail = report(reporter, "USER", target.id(), null, "OTHER", "  ");
        assertThat(noDetail.status()).isEqualTo(400);
        assertThat(noDetail.json().get("fieldErrors").get(0).get("field").asText()).isEqualTo("detail");
        Res tooLong = report(reporter, "USER", target.id(), null, "SPAM", "가".repeat(501));
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(tooLong.json().get("fieldErrors").get(0).get("field").asText()).isEqualTo("detail");
        Res badType = report(reporter, "MEMO", target.id(), null);
        assertThat(badType.json().get("fieldErrors").get(0).get("field").asText()).isEqualTo("targetType");

        assertThat(report(reporter, "USER", target.id(), null, "OTHER", "가".repeat(500)).status()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports", Integer.class)).isEqualTo(1);

        Res reasons = getAs(reporter, "/api/reports/reasons");
        assertThat(reasons.status()).isEqualTo(200);
        assertThat(reasons.json().get("detailMaxLength").asInt()).isEqualTo(500);
        assertThat(reasons.json().get("reasons").get(0).get("code").asText()).isEqualTo("SPAM");
        assertThat(reasons.body()).contains("\"code\":\"OTHER\"", "\"detailRequired\":true");
    }

    @Test
    void 자기_자신과_내_것은_신고할_수_없다() throws Exception {
        BlogFixture b = blog("rep-self", "PUBLIC");
        Member m = memberOf(b.blogId(), "멤버");
        long post = writePost(m, b.blogId(), "글", "내용");
        long cmt = comment(m, post, "댓글");

        assertThat(report(m, "USER", m.id(), null).code()).isEqualTo("SELF_NOT_ALLOWED");
        assertThat(report(m, "USER", m.id(), b.blogId()).code()).isEqualTo("SELF_NOT_ALLOWED");
        assertThat(report(m, "POST", post, b.blogId()).code()).isEqualTo("SELF_NOT_ALLOWED");
        assertThat(report(m, "COMMENT", cmt, b.blogId()).code()).isEqualTo("SELF_NOT_ALLOWED");
        Res own = report(b.owner(), "BLOG", b.blogId(), null);
        assertThat(own.status()).isEqualTo(400);
        assertThat(own.code()).isEqualTo("SELF_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports", Integer.class)).isZero();
    }

    @Test
    void 관리자는_신고하지_않고_볼_수_없는_글은_404_공지는_400() throws Exception {
        Member adm = admin("관리");
        Member target = member("대상");
        Res byAdmin = report(adm, "USER", target.id(), null);
        assertThat(byAdmin.status()).isEqualTo(403);
        assertThat(byAdmin.code()).isEqualTo("ADMIN_NOT_ALLOWED");

        BlogFixture priv = blog("rep-priv", "PRIVATE");
        long secret = writePost(priv.owner(), priv.blogId(), "비밀", "내용");
        Res hidden = report(target, "POST", secret, priv.blogId());
        assertThat(hidden.status()).isEqualTo(404);
        assertThat(hidden.code()).isEqualTo("POST_NOT_FOUND");
        assertThat(report(target, "COMMENT", 999_999, null).code()).isEqualTo("COMMENT_NOT_FOUND");
        assertThat(report(target, "BLOG", 999_999, null).code()).isEqualTo("BLOG_NOT_FOUND");

        long notice = postAs(adm, "/api/notices", postJson("공지", "내용", null, null)).json().get("id").asLong();
        Res n = report(target, "POST", notice, null);
        assertThat(n.status()).isEqualTo(400);
        assertThat(n.json().get("fieldErrors").get(0).get("field").asText()).isEqualTo("targetId");
    }

    @Test
    void 당시_내용이_남고_글이_정리된_뒤에도_남는다() throws Exception {
        BlogFixture b = blog("rep-snap", "PUBLIC");
        Member writer = memberOf(b.blogId(), "작성자");
        long post = writePost(writer, b.blogId(), "문제의 글", "문제의 내용");
        Member reporter = member("신고자");
        long id = report(reporter, "POST", post, b.blogId()).json().get("id").asLong();
        long userReport = report(reporter, "USER", writer.id(), null).json().get("id").asLong();

        deleteAs(writer, "/api/posts/" + post);
        clock.advance(Duration.ofDays(31));
        runDaily();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, post)).isZero();

        String snap = jdbc.queryForObject("SELECT target_snapshot FROM reports WHERE id = ?", String.class, id);
        assertThat(snap).contains("문제의 글", "문제의 내용", "작성자");
        String userSnap = jdbc.queryForObject("SELECT target_snapshot FROM reports WHERE id = ?", String.class,
                userReport);
        assertThat(userSnap).contains("작성자").doesNotContain(writer.email()).doesNotContain("010").doesNotContain("phone");
        assertThat(snap).doesNotContain(writer.email());
        assertThat(notificationCount(writer.id())).isZero();
    }
}
