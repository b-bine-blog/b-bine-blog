package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** 제한값을 설정만으로 바꾼다 (T021, FR-011, SC-003). */
@TestPropertySource(properties = "blog.limit.public=5")
class BlogLimitConfigTest extends BlogTestBase {

    @Test
    void 공개_제한을_5로_바꾸면_4번째가_만들어진다() throws Exception {
        Member m = member("설정");
        for (int i = 1; i <= 3; i++) {
            createBlog(m, "cfg-" + i, "PUBLIC", "FREE");
        }
        assertThat(postAs(m, "/api/blogs", blogJson("넷째", "cfg-4", "PUBLIC", "FREE")).status()).isEqualTo(201);
    }
}
