package com.mainblog.common.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 본문 변환과 XSS 거르기 (004 T006, FR-005, SC-002, D-64, research R-01). */
class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "[눌러](javascript:alert(1))",
            "<a href=\"javascript:alert(1)\">x</a>",
            "![x](data:image/png;base64,AAAA)",
            "<iframe src=\"https://evil.example\"></iframe>",
            "<p style=\"background:url(javascript:alert(1))\">x</p>",
            "<svg onload=alert(1)>",
            "<object data=\"x\"></object>",
            "<form action=\"https://evil.example\"><input type=\"submit\"></form>",
            "<a href=\"JaVaScRiPt:alert(1)\">x</a>",
            "<img src=\"//evil.example/a.png\">",
            "<div onclick=\"alert(1)\">x</div>",
            "<math><mi xlink:href=\"javascript:alert(1)\">x</mi></math>"
    })
    void 공격_문자열은_실행될_수_있는_형태로_남지_않는다(String attack) {
        String html = renderer.toSafeHtml(attack).toLowerCase();
        assertThat(html).doesNotContain("<script", "onerror", "onload", "onclick", "javascript:", "data:image",
                "<iframe", "style=", "<svg", "<object", "<form", "//evil.example/a.png");
    }

    @Test
    void 표_코드_취소선_강조_목록_인용은_남는다() {
        String md = """
                # 제목

                **굵게** *기울임* ~~취소~~

                | a | b |
                |---|---|
                | 1 | 2 |

                ```java
                int x = 1 < 2 ? 1 : 0;
                ```

                - 하나
                - 둘

                > 인용
                """;
        String html = renderer.toSafeHtml(md);
        assertThat(html).contains("<h1>제목</h1>", "<strong>굵게</strong>", "<em>기울임</em>", "<del>취소</del>",
                "<table>", "<td>1</td>", "<pre><code class=\"language-java\">int x = 1 &lt; 2", "<li>하나</li>",
                "<blockquote>");
    }

    @Test
    void 링크에는_rel이_붙고_우리_파일과_https_이미지는_남는다() {
        String html = renderer.toSafeHtml("[사이트](https://example.com) ![a](/files/3f2c.png) ![b](https://img.example/b.jpg)");
        assertThat(html).contains("href=\"https://example.com\"", "rel=\"nofollow noopener noreferrer\"",
                "src=\"/files/3f2c.png\"", "src=\"https://img.example/b.jpg\"");
        assertThat(html).doesNotContain("mainblog.invalid");
    }

    @Test
    void 허용된_HTML_태그는_남는다() {
        assertThat(renderer.toSafeHtml("<b>굵게</b>")).contains("<b>굵게</b>");
    }
}
