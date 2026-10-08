package com.mainblog.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.mainblog.support.IntegrationTestBase;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 블로그 목록·검색 응답 시간 (002 T073, SC-001). 블로그 1,000개에서 2초 안에 답해야 한다. */
class BlogResponseTimeTest extends IntegrationTestBase {

    static final long LIMIT_MS = 2000;
    static final int BLOGS = 1000;

    @BeforeEach
    void setUp() {
        long owner = createUser("owner@x.com", "Abcd123!", "블로그장");
        List<Object[]> blogs = new ArrayList<>();
        for (int i = 0; i < BLOGS; i++) {
            String visibility = i % 10 == 0 ? "PRIVATE" : "PUBLIC";
            blogs.add(new Object[] {"여행 블로그 " + i, "perf-" + i, "소개 " + i, visibility, 1 + i % 50});
        }
        jdbc.batchUpdate("INSERT INTO blogs (name, address, description, visibility, member_count) "
                + "VALUES (?, ?, ?, ?, ?)", blogs);
        jdbc.update("INSERT INTO blog_members (user_id, blog_id, role, joined_at) "
                + "SELECT ?, id, 'OWNER', NOW() FROM blogs", owner);
        jdbc.update("INSERT INTO tags (name) VALUES ('여행')");
        jdbc.update("INSERT INTO blog_tags (blog_id, tag_id) SELECT b.id, t.id FROM blogs b, tags t "
                + "WHERE MOD(b.id, 3) = 0");
    }

    private long elapsedMs(String url) throws Exception {
        long start = System.nanoTime();
        int status = mvc.perform(get(URI.create(url))).andReturn().getResponse().getStatus();
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(status).isEqualTo(200);
        return ms;
    }

    @Test
    void 블로그_1000개에서_목록과_검색이_2초_안에_답한다() throws Exception {
        elapsedMs("/api/blogs"); // 첫 요청의 클래스 로딩 시간을 뺀다

        assertThat(elapsedMs("/api/blogs?page=40&size=20")).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/blogs?sort=popular&page=3&size=20")).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/blogs/search?q=%EC%97%AC%ED%96%89")).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/blogs/search?q=%23%EC%97%AC%ED%96%89&sort=popular")).isLessThan(LIMIT_MS);
    }
}
