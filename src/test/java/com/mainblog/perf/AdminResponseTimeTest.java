package com.mainblog.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.mainblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 관리자 화면 응답 시간 (005 T055, SC-001). 회원 10,000명, 블로그 1,000개, 신고 5,000건에서
 * 회원 검색·블로그 검색·신고함이 2초 안에 답해야 한다.
 */
class AdminResponseTimeTest extends IntegrationTestBase {

    static final long LIMIT_MS = 2000;
    static final int MEMBERS = 10_000;
    static final int BLOGS = 1_000;
    static final int REPORTS = 5_000;

    private long elapsedMs(String url, Cookie... cookies) throws Exception {
        long start = System.nanoTime();
        int status = mvc.perform(get(URI.create(url)).cookie(cookies)).andReturn().getResponse().getStatus();
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(status).as(url).isEqualTo(200);
        return ms;
    }

    @Test
    void 회원_만명_블로그_천개_신고_오천건에서_2초_안에_답한다() throws Exception {
        long admin = createUser("admin@x.com", "Abcd123!", "관리자");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin);
        List<Object[]> users = new ArrayList<>();
        for (int i = 0; i < MEMBERS; i++) {
            users.add(new Object[] {"m" + i + "@x.com", "회원" + i, String.format("010%08d", i), BASE_TIME});
        }
        jdbc.batchUpdate("INSERT INTO users (email, password_hash, name, nickname, phone, role, status, created_at) "
                + "VALUES (?, 'x', '회원', ?, ?, 'USER', 'ACTIVE', ?)", users);
        Long first = jdbc.queryForObject("SELECT MIN(id) FROM users WHERE email LIKE 'm%@x.com'", Long.class);

        List<Object[]> blogs = new ArrayList<>();
        for (int i = 0; i < BLOGS; i++) {
            blogs.add(new Object[] {"블로그" + i, "perf-blog-" + i, i % 3 == 0 ? "PRIVATE" : "PUBLIC", BASE_TIME});
        }
        jdbc.batchUpdate("INSERT INTO blogs (name, address, visibility, join_policy, status, member_count, hidden, "
                + "created_at) VALUES (?, ?, ?, 'FREE', 'ACTIVE', 1, FALSE, ?)", blogs);
        jdbc.update("INSERT INTO blog_members (user_id, blog_id, role, joined_at) "
                + "SELECT ? + (b.id % ?), b.id, 'OWNER', ? FROM blogs b", first, MEMBERS, BASE_TIME);
        Long firstBlog = jdbc.queryForObject("SELECT MIN(id) FROM blogs", Long.class);

        List<Object[]> reports = new ArrayList<>();
        for (int i = 0; i < REPORTS; i++) {
            long blog = firstBlog + (i % BLOGS);
            reports.add(new Object[] {blog, first + (i % MEMBERS), blog, i % 4 == 0 ? "DONE" : "PENDING",
                    BASE_TIME.minusMinutes(REPORTS - i)});
        }
        jdbc.batchUpdate("INSERT INTO reports (blog_id, reporter_id, target_type, target_id, target_snapshot, reason, "
                + "route, status, created_at) VALUES (?, ?, 'BLOG', ?, '{}', 'SPAM', 'ADMIN', ?, ?)", reports);
        jdbc.update("UPDATE reports SET result = 'NONE', handled_by = ?, handled_at = created_at WHERE status = 'DONE'",
                admin);

        Cookie[] login = loginAs("admin@x.com", "Abcd123!", false).cookies();
        elapsedMs("/api/admin/stats", login); // 첫 요청의 클래스 로딩 시간을 뺀다

        assertThat(elapsedMs("/api/admin/members?type=NICKNAME&q=" + enc("회원99"), login)).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/admin/members?type=EMAIL&q=m9999@x.com", login)).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/admin/blogs", login)).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/admin/blogs?q=" + enc("블로그5"), login)).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/admin/blogs?page=50", login)).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/admin/reports", login)).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/admin/reports?status=DONE&page=10", login)).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/admin/stats", login)).isLessThan(LIMIT_MS);
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
