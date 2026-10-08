package com.mainblog.common.markdown;

import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 글 본문 마크다운 → 걸러낸 HTML (004 T006, FR-005, SEC-06, D-64, D-74, research R-01).
 * 읽을 때마다 commonmark로 바꾸고 jsoup 허용 목록으로 거른다. 허용 목록을 바꾸면 예전 글에도 바로 적용된다.
 * 화면은 이 결과(contentHtml)만 innerHTML로 넣는다.
 */
@Component
public class MarkdownRenderer {

    /** 상대 주소(/files/..)를 허용 목록의 http·https 검사에 통과시키려고 붙이는 가짜 기준 주소. 결과에는 남지 않는다. */
    private static final String BASE_URI = "https://mainblog.invalid/";
    private static final String FILE_PREFIX = "/files/";
    private static final String LINK_REL = "nofollow noopener noreferrer";

    private static final List<Extension> EXTENSIONS =
            List.of(TablesExtension.create(), StrikethroughExtension.create());

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).build();
    private final Cleaner cleaner = new Cleaner(safelist());

    /** 마크다운 원문을 화면에 넣어도 되는 HTML로 바꾼다. */
    public String toSafeHtml(String markdown) {
        String raw = renderer.render(parser.parse(markdown == null ? "" : markdown));
        Document clean = cleaner.clean(Jsoup.parseBodyFragment(raw, BASE_URI));
        for (Element a : clean.select("a[href]")) {
            a.attr("rel", LINK_REL);
        }
        for (Element img : clean.select("img")) {
            if (!allowedImage(img.attr("src"))) {
                img.remove();
            }
        }
        clean.outputSettings().prettyPrint(false);
        return clean.body().html();
    }

    /** 이미지는 우리 서버 파일(/files/)과 http·https만. 프로토콜 상대 주소(//)·javascript:·data:는 지운다. */
    static boolean allowedImage(String src) {
        String s = src == null ? "" : src.strip().toLowerCase();
        return s.startsWith(FILE_PREFIX) || s.startsWith("http://") || s.startsWith("https://");
    }

    private static Safelist safelist() {
        return Safelist.relaxed()
                .addTags("del", "s", "hr", "input")
                .addAttributes("input", "type", "checked", "disabled")
                .addAttributes("th", "align")
                .addAttributes("td", "align")
                .addAttributes("code", "class")
                .removeProtocols("a", "href", "ftp")
                .preserveRelativeLinks(true);
    }
}
