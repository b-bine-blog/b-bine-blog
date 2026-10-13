package com.mainblog.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.mainblog.support.IntegrationTestBase;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 글 목록·검색·피드 응답 시간 (004 T057, SC-001). 글 5,000개에서 2초 안에 답해야 한다. */
class PostResponseTimeTest extends IntegrationTestBase {

    static final long LIMIT_MS = 2000;
    static final int POSTS = 5000;
    static final int BLOGS = 50;

    long firstBlog;

    @BeforeEach
    void setUp() {
        long owner = createUser("post-perf@x.com", "Abcd123!", "작가");
        List<Object[]> blogs = new ArrayList<>();
        for (int i = 0; i < BLOGS; i++) {
            blogs.add(new Object[] {"블로그 " + i, "post-perf-" + i, i % 10 == 0 ? "PRIVATE" : "PUBLIC"});
        }
        jdbc.batchUpdate("INSERT INTO blogs (name, address, visibility) VALUES (?, ?, ?)", blogs);
        firstBlog = jdbc.queryForObject("SELECT MIN(id) FROM blogs WHERE address LIKE 'post-perf-%'", Long.class);
        jdbc.update("INSERT INTO blog_members (user_id, blog_id, role, joined_at) "
                + "SELECT ?, id, 'OWNER', NOW() FROM blogs WHERE address LIKE 'post-perf-%'", owner);

        LocalDateTime base = LocalDateTime.now().minusDays(10);
        String body = "제주 여행 기록입니다. ".repeat(40);
        List<Object[]> posts = new ArrayList<>();
        for (int i = 0; i < POSTS; i++) {
            String title = (i % 7 == 0 ? "여행 " : "일상 ") + i;
            posts.add(new Object[] {firstBlog + i % BLOGS, owner, title, body, i % 30, base.plusMinutes(i),
                    base.plusMinutes(i)});
        }
        jdbc.batchUpdate("INSERT INTO posts (blog_id, author_id, board_type, title, content, status, like_count, "
                + "created_at, updated_at) VALUES (?, ?, 'BLOG', ?, ?, 'VISIBLE', ?, ?, ?)", posts);
        jdbc.update("INSERT INTO tags (name) VALUES ('여행')");
        jdbc.update("INSERT INTO post_tags (post_id, tag_id) SELECT p.id, t.id FROM posts p, tags t "
                + "WHERE t.name = '여행' AND MOD(p.id, 5) = 0");
    }

    private long elapsedMs(String url) throws Exception {
        long start = System.nanoTime();
        int status = mvc.perform(get(URI.create(url))).andReturn().getResponse().getStatus();
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(status).as(url).isEqualTo(200);
        return ms;
    }

    @Test
    void 글_5000개에서_목록_검색_피드가_2초_안에_답한다() throws Exception {
        elapsedMs("/api/feed"); // 첫 요청의 클래스 로딩 시간을 뺀다
        String travel = "%EC%97%AC%ED%96%89";

        assertThat(elapsedMs("/api/blogs/" + (firstBlog + 1) + "/posts?page=3&size=30")).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/feed?tab=LATEST&page=50")).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/feed?tab=POPULAR&page=10")).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/search?q=" + travel)).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/search?q=" + travel + "&type=TITLE&sort=POPULAR&page=5")).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/tags/" + travel + "/posts?page=10")).isLessThan(LIMIT_MS);
    }
}
