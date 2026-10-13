package com.mainblog.common.markdown;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 걸러낸 본문 HTML에서 요약 글자·첫 이미지·우리 서버 이미지 이름을 뽑는다 (004 T007, D-100 #7·#14, research R-03, R-12).
 * 입력은 {@link MarkdownRenderer#toSafeHtml}의 결과라서 허용되지 않은 이미지는 이미 빠져 있다.
 */
public final class MarkdownText {

    /** FileStorage가 만든 저장 이름(UUID + 확장자). */
    private static final Pattern STORED_NAME = Pattern.compile("^/files/([0-9a-fA-F-]{32,40}\\.(?:jpg|jpeg|png|gif|webp))$");

    private MarkdownText() {
    }

    /** 마크다운 기호·태그를 뺀 글자를 공백 하나로 이어 앞 maxLength자(코드 포인트)만. og:description(D-100 #14). */
    public static String summary(String safeHtml, int maxLength) {
        String text = Jsoup.parseBodyFragment(safeHtml).text().strip();
        if (text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxLength));
    }

    /** 본문에 처음 나오는 이미지 주소. 없으면 null(D-100 #7). */
    public static String firstImage(String safeHtml) {
        Element img = Jsoup.parseBodyFragment(safeHtml).selectFirst("img[src]");
        return img == null ? null : img.attr("src");
    }

    /** 본문 이미지 가운데 우리 서버 파일의 저장 이름(나온 순서, 중복 없음). 글과 이미지를 잇는 데 쓴다(R-03). */
    public static List<String> fileImageNames(String safeHtml) {
        Document doc = Jsoup.parseBodyFragment(safeHtml);
        Set<String> names = new LinkedHashSet<>();
        for (Element img : doc.select("img[src]")) {
            Matcher m = STORED_NAME.matcher(img.attr("src").strip());
            if (m.matches()) {
                names.add(m.group(1));
            }
        }
        return new ArrayList<>(names);
    }
}
