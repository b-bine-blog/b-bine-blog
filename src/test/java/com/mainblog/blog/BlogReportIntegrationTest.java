package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** US7 블로그 안 신고 처리 (T060, BLG-13, FR-052, D-93). 신고 만들기는 003이라 행을 직접 넣는다. */
class BlogReportIntegrationTest extends BlogTestBase {

    private long post(long blog, long author) {
        jdbc.update("INSERT INTO posts (blog_id, author_id, title, content) VALUES (?, ?, '글', '본문')", blog, author);
        return jdbc.queryForObject("SELECT MAX(id) FROM posts", Long.class);
    }

    private Res resolve(Member actor, long blog, long report, String body) throws Exception {
        return postAs(actor, "/api/blogs/" + blog + "/reports/" + report + "/resolution", body);
    }

    @Test
    void 블로그_접수_신고만_보이고_처리하면_신고자에게_결과가_간다() throws Exception {
        Member owner = member("장");
        long blog = createBlog(owner, "report-blog", "PUBLIC", "FREE");
        Member writer = member("작성");
        addMember(blog, writer.id(), "MEMBER");
        Member reporter = member("신고자");
        long postId = post(blog, writer.id());
        long r1 = insertBlogReport(blog, reporter.id(), "POST", postId);
        long adminRoute = insertBlogReport(blog, reporter.id(), "POST", post(blog, owner.id()));
        jdbc.update("UPDATE reports SET route = 'ADMIN' WHERE id = ?", adminRoute);

        Res list = getAs(owner, "/api/blogs/" + blog + "/reports");
        assertThat(list.json().get("totalCount").asInt()).isEqualTo(1);
        assertThat(list.json().get("items").get(0).get("targetUserId").asLong()).isEqualTo(writer.id());
        assertThat(list.body()).doesNotContain("reporterId");
        assertThat(resolve(owner, blog, adminRoute, "{\"result\":\"NONE\"}").status()).isEqualTo(404);

        Res done = resolve(owner, blog, r1, "{\"result\":\"WARNING\",\"reason\":\"주의\"}");
        assertThat(done.status()).isEqualTo(200);
        assertThat(done.json().get("status").asText()).isEqualTo("DONE");
        assertThat(done.json().get("result").asText()).isEqualTo("WARNING");
        assertThat(notificationsOf(reporter.id())).containsExactly("REPORT_RESOLVED");
        assertThat(notificationsOf(writer.id())).containsExactly("MEMBER_WARNED");
        assertThat(jdbc.queryForObject("SELECT report_id FROM blog_sanctions WHERE user_id = ?", Long.class,
                writer.id())).isEqualTo(r1);
        assertThat(resolve(owner, blog, r1, "{\"result\":\"NONE\"}").code()).isEqualTo("REPORT_ALREADY_HANDLED");
        assertThat(getAs(owner, "/api/blogs/" + blog + "/reports?status=DONE").json().get("totalCount").asInt())
                .isEqualTo(1);
    }

    @Test
    void 처리_결과는_네_가지다() throws Exception {
        Member owner = member("장2");
        long blog = createBlog(owner, "report-four", "PUBLIC", "FREE");
        Member reporter = member("신고자2");
        Member a = member("문제없음");
        Member b = member("정지됨");
        Member c = member("강퇴됨");
        for (Member m : new Member[] {a, b, c}) {
            addMember(blog, m.id(), "MEMBER");
        }
        long ra = insertBlogReport(blog, reporter.id(), "USER", a.id());
        long rb = insertBlogReport(blog, reporter.id(), "POST", post(blog, b.id()));
        jdbc.update("INSERT INTO comments (author_id, post_id, content) VALUES (?, ?, '댓글')", c.id(), post(blog, a.id()));
        long commentId = jdbc.queryForObject("SELECT MAX(id) FROM comments", Long.class);
        long rc = insertBlogReport(blog, reporter.id(), "COMMENT", commentId);

        assertThat(resolve(owner, blog, ra, "{\"result\":\"NONE\"}").status()).isEqualTo(200);
        assertThat(notificationsOf(a.id())).isEmpty();
        assertThat(resolve(owner, blog, rb, "{\"result\":\"SUSPEND\"}").status()).isEqualTo(400);
        assertThat(resolve(owner, blog, rb, "{\"result\":\"SUSPEND\",\"period\":\"DAYS_30\"}").status()).isEqualTo(200);
        assertThat(getAs(b, "/api/blogs/address/report-four").code()).isEqualTo("BLOG_SUSPENDED");
        assertThat(resolve(owner, blog, rc, "{\"result\":\"KICK\"}").status()).isEqualTo(200);
        assertThat(role(blog, c.id())).isNull();
        assertThat(notificationsOf(reporter.id())).hasSize(3);
        assertThat(resolve(owner, blog, ra, "{\"result\":\"BAN\"}").status()).isIn(400, 409);
    }

    @Test
    void 멤버_관리_권한이_없으면_못_본다() throws Exception {
        Member owner = member("장3");
        long blog = createBlog(owner, "report-perm", "PUBLIC", "FREE");
        Member m = member("멤버3");
        addMember(blog, m.id(), "MEMBER");
        long r = insertBlogReport(blog, m.id(), "USER", owner.id());
        assertThat(getAs(m, "/api/blogs/" + blog + "/reports").status()).isEqualTo(403);
        assertThat(resolve(m, blog, r, "{\"result\":\"NONE\"}").status()).isEqualTo(403);
    }
}
