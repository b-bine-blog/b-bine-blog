package com.mainblog.common.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 요약·첫 이미지·이미지 이름 뽑기 (004 T007, D-100 #7·#14). */
class MarkdownTextTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void 요약은_마크다운_기호를_빼고_공백을_정리한_앞_n자() {
        String html = renderer.toSafeHtml("# 제목\n\n**굵은** 글과   [링크](https://a.example)\n\n- 목록");
        assertThat(MarkdownText.summary(html, 100)).isEqualTo("제목 굵은 글과 링크 목록");
        assertThat(MarkdownText.summary(html, 4)).isEqualTo("제목 굵");
    }

    @Test
    void 이모지도_한_글자로_자른다() {
        String html = renderer.toSafeHtml("😀😀😀");
        assertThat(MarkdownText.summary(html, 2)).isEqualTo("😀😀");
    }

    @Test
    void 첫_이미지와_우리_파일_이름을_순서대로_중복_없이() {
        String a = "/files/0b0e9c7e-1111-4222-8333-444455556666.png";
        String b = "/files/0b0e9c7e-1111-4222-8333-444455557777.webp";
        String html = renderer.toSafeHtml("글 ![x](https://img.example/x.jpg) ![a](" + a + ") ![b](" + b + ") ![a](" + a
                + ") [링크](" + b.replace("7777", "8888") + ")");
        assertThat(MarkdownText.firstImage(html)).isEqualTo("https://img.example/x.jpg");
        assertThat(MarkdownText.fileImageNames(html)).containsExactly(a.substring(7), b.substring(7));
    }

    @Test
    void 이미지가_없으면_null() {
        assertThat(MarkdownText.firstImage(renderer.toSafeHtml("글만"))).isNull();
    }
}
