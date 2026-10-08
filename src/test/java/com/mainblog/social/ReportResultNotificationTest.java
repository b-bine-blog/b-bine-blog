package com.mainblog.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.social.application.ReportResultNotifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 신고 처리 결과 알림 (003 T034, US6-5~7, FR-051, D-38, research R-15). 002 블로그장 처리와 005 관리자 처리가 같은 부품을 쓴다. */
class ReportResultNotificationTest extends SocialTestBase {

    @Autowired
    ReportResultNotifier notifier;

    private long reportVia(Member reporter, long blogId, long postId) throws Exception {
        Res r = postAs(reporter, "/api/reports", reportJson("POST", postId, blogId, "SPAM", null));
        assertThat(r.status()).isEqualTo(201);
        return r.json().get("id").asLong();
    }

    private Res resolve(Member actor, long blogId, long reportId, String body) throws Exception {
        return postAs(actor, "/api/blogs/" + blogId + "/reports/" + reportId + "/resolution", body);
    }

    private String status(long reportId) {
        return jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, reportId);
    }

    @Test
    void 블로그장이_네_가지_결과로_처리하면_신고자에게_운영_탭_알림이_한_건씩_간다() throws Exception {
        BlogFixture b = blog("res-four", "PUBLIC");
        List<String> bodies = List.of("{\"result\":\"NONE\"}", "{\"result\":\"WARNING\",\"reason\":\"주의\"}",
                "{\"result\":\"SUSPEND\",\"period\":\"DAYS_30\"}", "{\"result\":\"KICK\"}");
        List<String> labels = List.of("문제 없음", "경고", "정지", "강제 퇴장");
        for (int i = 0; i < bodies.size(); i++) {
            Member writer = memberOf(b.blogId(), "작성" + i);
            long post = writePost(writer, b.blogId(), "글" + i, "내용");
            Member reporter = member("신고" + i);
            long report = reportVia(reporter, b.blogId(), post);

            assertThat(resolve(b.owner(), b.blogId(), report, bodies.get(i)).status()).isEqualTo(200);
            assertThat(notificationTabsOf(reporter.id())).containsExactly("REPORT_RESOLVED:OPERATION");
            String content = jdbc.queryForObject("SELECT content FROM notifications WHERE recipient_id = ?",
                    String.class, reporter.id());
            assertThat(content).contains(labels.get(i)).doesNotContain("작성" + i);
            // 운영 탭 목록에도 나온다
            assertThat(getAs(reporter, "/api/notifications?tab=OPERATION").json().get("items").size()).isEqualTo(1);
        }
    }

    @Test
    void 결과_알림을_끄면_알림은_없고_처리는_된다() throws Exception {
        BlogFixture b = blog("res-off", "PUBLIC");
        Member writer = memberOf(b.blogId(), "작성");
        long post = writePost(writer, b.blogId(), "글", "내용");
        Member reporter = member("신고자");
        long report = reportVia(reporter, b.blogId(), post);
        setNotification(reporter.id(), "report_result", false);

        assertThat(resolve(b.owner(), b.blogId(), report, "{\"result\":\"NONE\"}").status()).isEqualTo(200);
        assertThat(status(report)).isEqualTo("DONE");
        assertThat(notificationCount(reporter.id())).isZero();
    }

    @Test
    void 신고자가_탈퇴했으면_알림이_없다() throws Exception {
        BlogFixture b = blog("res-gone", "PUBLIC");
        Member writer = memberOf(b.blogId(), "작성");
        long post = writePost(writer, b.blogId(), "글", "내용");
        Member reporter = member("신고자");
        long report = reportVia(reporter, b.blogId(), post);
        withdraw(reporter.id());

        assertThat(resolve(b.owner(), b.blogId(), report, "{\"result\":\"NONE\"}").status()).isEqualTo(200);
        assertThat(status(report)).isEqualTo("DONE");
        assertThat(notificationCount(reporter.id())).isZero();
    }

    @Test
    void 관리자_처리_결과_문구를_만든다() throws Exception {
        BlogFixture b = blog("res-admin", "PUBLIC");
        Member reporter = member("신고자");
        long report = postAs(reporter, "/api/reports", reportJson("BLOG", b.blogId(), null, "SPAM", null)).json()
                .get("id").asLong();
        jdbc.update("UPDATE reports SET status = 'DONE', result = 'BLOG_CLOSE' WHERE id = ?", report);

        assertThat(notifier.resolved(report)).isTrue();
        assertThat(notifier.resolved(report)).isFalse(); // 같은 처리는 한 번만
        String content = jdbc.queryForObject("SELECT content FROM notifications WHERE recipient_id = ?", String.class,
                reporter.id());
        assertThat(content).contains("블로그", "블로그 폐쇄");
        assertThat(notificationTabsOf(reporter.id())).containsExactly("REPORT_RESOLVED:OPERATION");

        assertThat(ReportResultNotifier.resultLabel("OWNER_WARNING")).isEqualTo("블로그장 경고");
        assertThat(ReportResultNotifier.resultLabel("OWNER_KICK")).isEqualTo("블로그장 강퇴");
        assertThat(ReportResultNotifier.resultLabel("NONE")).isEqualTo("문제 없음");
    }
}
