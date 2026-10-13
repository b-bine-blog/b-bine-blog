package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.mainblog.board.BoardTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

/** 블로그 배경 이미지 (D-121). 없으면 기본 배경(null), 올리면 블로그 상세에 주소가 오고, 대표 이미지와 따로 바뀐다. */
class BlogBackgroundImageIntegrationTest extends BoardTestBase {

    private MockMultipartFile file() throws Exception {
        return new MockMultipartFile("file", "bg.png", "image/png", image("png", 32));
    }

    @Test
    void 배경_이미지는_올리면_보이고_지우면_기본으로_돌아간다() throws Exception {
        Member owner = member("배경장");
        long b = createBlog(owner, "bg-blog", "PUBLIC", "FREE");
        assertThat(getAs(null, "/api/blogs/address/bg-blog").json().get("backgroundImageUrl").isNull()).isTrue();

        Res up = call(owner, multipart(HttpMethod.PUT, "/api/blogs/" + b + "/background-image").file(file()));
        assertThat(up.status()).isEqualTo(200);
        String url = up.json().get("backgroundImageUrl").asText();
        assertThat(url).startsWith("/files/");

        var detail = getAs(null, "/api/blogs/address/bg-blog").json();
        assertThat(detail.get("backgroundImageUrl").asText()).isEqualTo(url);
        assertThat(detail.get("coverImageUrl").isNull()).as("대표 이미지와 따로 저장한다").isTrue();

        assertThat(deleteAs(owner, "/api/blogs/" + b + "/background-image").status()).isEqualTo(204);
        assertThat(getAs(null, "/api/blogs/address/bg-blog").json().get("backgroundImageUrl").isNull()).isTrue();
    }

    @Test
    void 배경_이미지_올리기는_정보_수정_권한이_없으면_403() throws Exception {
        Member owner = member("배경장2");
        long b = createBlog(owner, "bg-forbid", "PUBLIC", "FREE");
        Member m = member("배경멤버");
        addMember(b, m.id(), "MEMBER");

        Res r = call(m, multipart(HttpMethod.PUT, "/api/blogs/" + b + "/background-image").file(file()));
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("BLOG_FORBIDDEN");
    }
}
