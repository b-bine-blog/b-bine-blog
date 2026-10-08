package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.mainblog.admin.application.ActionType;
import com.mainblog.admin.application.AdminActionLog;
import com.mainblog.admin.application.TargetType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 관리자 활동 기록 (005 T045, US6-1, FR-039, SC-004, D-103 #20). */
class AdminActionLogIntegrationTest extends AdminTestBase {

    @Autowired
    AdminActionLog actionLog;

    @Autowired
    PlatformTransactionManager transactionManager;

    private long one(String type) {
        List<Map<String, Object>> rows = actions(type);
        assertThat(rows).as(type).hasSize(1);
        return ((Number) rows.get(0).get("target_id")).longValue();
    }

    private String targetType(String type) {
        return (String) actions(type).get(0).get("target_type");
    }

    @Test
    void 열여섯_가지_조치마다_관리자_종류_대상이_맞는_행이_하나씩_남는다() throws Exception {
        Member admin = admin("기록관리자");
        BlogFixture a = blog("log-a", "PUBLIC");
        BlogFixture b = blog("log-b", "PUBLIC");
        Member s = member("기록부장");
        addMember(b.blogId(), s.id(), "MANAGER");
        BlogFixture c = blog("log-c", "PUBLIC");
        Member writer = memberOf(a.blogId(), "기록작성자");
        long post = writePost(writer, a.blogId(), "기록글", "본문");
        long comment = postAs(writer, "/api/posts/" + post + "/comments", json(Map.of("content", "기록댓글")))
                .json().get("id").asLong();
        Member reporter = member("기록신고자");
        long report = insertAdminReport(a.blogId(), reporter.id(), "BLOG", a.blogId());

        String base = "/api/admin/";
        assertThat(postAs(admin, base + "members/" + writer.id() + "/privacy-view", null).status()).isEqualTo(200);
        putAs(admin, base + "blogs/" + a.blogId() + "/hidden", null);
        deleteAs(admin, base + "blogs/" + a.blogId() + "/hidden");
        postAs(admin, base + "blogs/" + b.blogId() + "/owner-warnings",
                json(Map.of("ownerId", b.owner().id(), "reason", "기록 경고")));
        postAs(admin, base + "blogs/" + b.blogId() + "/owner-revocation", "{\"ownerId\":" + b.owner().id() + "}");
        postAs(admin, base + "blogs/" + c.blogId() + "/owner-revocation", "{\"ownerId\":" + c.owner().id() + "}");
        putAs(admin, base + "posts/" + post + "/hidden", null);
        deleteAs(admin, base + "posts/" + post + "/hidden");
        putAs(admin, base + "comments/" + comment + "/hidden", null);
        deleteAs(admin, base + "comments/" + comment + "/hidden");
        deleteAs(admin, base + "comments/" + comment);
        deleteAs(admin, base + "posts/" + post);
        postAs(admin, base + "posts/" + post + "/restore", null);
        postAs(admin, base + "reports/" + report + "/resolution", "{\"result\":\"NONE\"}");
        postAs(admin, base + "blogs/" + a.blogId() + "/force-close", null);

        // 자동 위임과 폐쇄 예약을 둘 다 보려고 두 블로그장을 강퇴해서 OWNER_KICK만 두 행이다
        List<String> expected = new ArrayList<>(Arrays.stream(ActionType.values()).map(Enum::name).toList());
        expected.add("OWNER_KICK");
        assertThat(actionTypes()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(count("SELECT COUNT(*) FROM admin_actions WHERE admin_id <> ?", admin.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM admin_actions WHERE created_at <> ?", clock.now())).isZero();

        assertThat(one("PRIVACY_VIEW")).isEqualTo(writer.id());
        assertThat(targetType("PRIVACY_VIEW")).isEqualTo("USER");
        assertThat(one("BLOG_HIDE")).isEqualTo(a.blogId());
        assertThat(one("BLOG_UNHIDE")).isEqualTo(a.blogId());
        assertThat(one("BLOG_FORCE_CLOSE")).isEqualTo(a.blogId());
        assertThat(one("OWNER_WARNING")).isEqualTo(b.owner().id());
        assertThat(targetType("OWNER_WARNING")).isEqualTo("USER");
        assertThat(actions("OWNER_KICK")).extracting(r -> ((Number) r.get("target_id")).longValue())
                .containsExactly(b.owner().id(), c.owner().id());
        assertThat(one("AUTO_DELEGATE")).isEqualTo(b.blogId());
        assertThat(one("CLOSE_SCHEDULE")).isEqualTo(c.blogId());
        assertThat(one("POST_HIDE")).isEqualTo(post);
        assertThat(one("POST_UNHIDE")).isEqualTo(post);
        assertThat(one("POST_DELETE")).isEqualTo(post);
        assertThat(one("RESTORE")).isEqualTo(post);
        assertThat(one("COMMENT_HIDE")).isEqualTo(comment);
        assertThat(one("COMMENT_UNHIDE")).isEqualTo(comment);
        assertThat(one("COMMENT_DELETE")).isEqualTo(comment);
        assertThat(targetType("COMMENT_DELETE")).isEqualTo("COMMENT");
        assertThat(one("REPORT_HANDLE")).isEqualTo(report);
        assertThat(targetType("REPORT_HANDLE")).isEqualTo("REPORT");
    }

    @Test
    void 목록은_종류_관리자_대상_기간으로_거르고_탈퇴_대상과_지워진_대상을_표시한다() throws Exception {
        Member a1 = admin("거르기관리1");
        Member a2 = admin("거르기관리2");
        BlogFixture b = blog("log-filter", "PUBLIC");
        Member gone = member("떠날회원");
        long post = writePost(b.owner(), b.blogId(), "지울글", "본문");
        long comment = postAs(gone, "/api/posts/" + post + "/comments", json(Map.of("content", "사라질 댓글")))
                .json().get("id").asLong();
        postAs(a1, "/api/admin/members/" + gone.id() + "/privacy-view", null);
        putAs(a1, "/api/admin/blogs/" + b.blogId() + "/hidden", null);
        clock.advance(java.time.Duration.ofDays(2));
        Member admin2 = relogin(a2);
        putAs(admin2, "/api/admin/comments/" + comment + "/hidden", null);
        putAs(admin2, "/api/admin/posts/" + post + "/hidden", null);

        JsonNode all = getAs(admin2, "/api/admin/actions").json();
        assertThat(all.get("totalElements").asInt()).isEqualTo(4);
        List<String> types = new ArrayList<>();
        all.get("items").forEach(i -> types.add(i.get("actionType").asText()));
        assertThat(types).containsExactly("POST_HIDE", "COMMENT_HIDE", "BLOG_HIDE", "PRIVACY_VIEW");
        assertThat(all.get("items").get(0).get("admin").get("nickname").asText()).isEqualTo("거르기관리2");
        assertThat(all.get("items").get(0).get("targetLabel").asText()).isEqualTo("지울글");
        assertThat(all.get("items").get(2).get("targetLabel").asText()).isEqualTo("블로그 log-filter");

        assertThat(getAs(admin2, "/api/admin/actions?actionType=BLOG_HIDE").json().get("totalElements").asInt())
                .isEqualTo(1);
        assertThat(getAs(admin2, "/api/admin/actions?adminId=" + a1.id()).json().get("totalElements").asInt())
                .isEqualTo(2);
        assertThat(getAs(admin2, "/api/admin/actions?targetType=POST&targetId=" + post).json()
                .get("totalElements").asInt()).isEqualTo(1);
        String today = clock.now().toLocalDate().toString();
        String twoDaysAgo = clock.now().toLocalDate().minusDays(2).toString();
        assertThat(getAs(admin2, "/api/admin/actions?from=" + today).json().get("totalElements").asInt()).isEqualTo(2);
        assertThat(getAs(admin2, "/api/admin/actions?to=" + twoDaysAgo).json().get("totalElements").asInt())
                .isEqualTo(2);
        assertThat(getAs(admin2, "/api/admin/actions?actionType=WRONG").status()).isEqualTo(400);
        assertThat(getAs(admin2, "/api/admin/actions?from=2026-13-01").status()).isEqualTo(400);

        withdraw(gone.id());
        jdbc.update("DELETE FROM comments WHERE id = ?", comment);
        JsonNode after = getAs(admin2, "/api/admin/actions").json().get("items");
        assertThat(after.get(1).get("targetLabel").asText()).isEqualTo("삭제된 대상");
        assertThat(after.get(3).get("targetLabel").asText()).isEqualTo("탈퇴한 회원");

        // 고치거나 지우는 경로가 없다(D-103 #20)
        long id = after.get(0).get("id").asLong();
        assertThat(putAs(admin2, "/api/admin/actions/" + id, "{\"memo\":\"x\"}").status()).isIn(404, 405);
        assertThat(deleteAs(admin2, "/api/admin/actions/" + id).status()).isIn(404, 405);
        assertThat(deleteAs(admin2, "/api/admin/actions").status()).isIn(404, 405);
        assertThat(count("SELECT COUNT(*) FROM admin_actions")).isEqualTo(4);
    }

    @Test
    void 조치가_되돌려지면_기록도_없고_기록은_조치와_같은_트랜잭션에서만_쓴다() throws Exception {
        Member admin = admin("되돌림관리");
        BlogFixture b = blog("log-rollback", "PUBLIC");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE blogs SET hidden = TRUE WHERE id = ?", b.blogId());
            actionLog.write(admin.id(), ActionType.BLOG_HIDE, TargetType.BLOG, b.blogId(), null, null);
            throw new IllegalStateException("조치 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(actionTypes()).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM blogs WHERE id = ? AND hidden = TRUE", b.blogId())).isZero();
        assertThatThrownBy(() -> actionLog.write(admin.id(), ActionType.BLOG_HIDE, TargetType.BLOG, b.blogId(), null,
                null)).isNotNull();
        assertThat(actionTypes()).isEmpty();

        // 실패한 API 조치(409)도 기록이 없다
        jdbc.update("UPDATE blogs SET status = 'CLOSED', closed_at = ? WHERE id = ?", clock.now(), b.blogId());
        assertThat(postAs(admin, "/api/admin/blogs/" + b.blogId() + "/force-close", null).status()).isEqualTo(409);
        assertThat(actionTypes()).isEmpty();
    }
}
