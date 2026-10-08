package com.mainblog.common.tag;

import com.mainblog.common.error.InvalidInputException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 태그 규칙 (T010, 6.4, D-88, D-96). 블로그 태그와 글 태그(004)가 같이 쓴다.
 * 앞의 #과 앞뒤 공백 제거, 가운데 공백은 _, 영문은 소문자, 1~최대 글자 한글·영문·숫자·_, 같은 태그는 하나로. 금칙어 없음.
 */
public final class TagNormalizer {

    private static final Pattern ALLOWED = Pattern.compile("^[가-힣a-z0-9_]+$");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TagNormalizer() {
    }

    /** 태그 하나. 규칙에 맞지 않으면 null. */
    public static String normalize(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        while (s.startsWith("#")) {
            s = s.substring(1).strip();
        }
        s = SPACES.matcher(s).replaceAll("_").toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.codePointCount(0, s.length()) > maxLength || !ALLOWED.matcher(s).matches()) {
            return null;
        }
        return s;
    }

    /** 태그 목록. 하나라도 규칙에 맞지 않거나, 합친 뒤 maxCount를 넘으면 400(field = "tags"). */
    public static List<String> normalizeAll(List<String> raws, int maxCount, int maxLength) {
        if (raws == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String raw : raws) {
            String tag = normalize(raw, maxLength);
            if (tag == null) {
                throw new InvalidInputException("tags", "Pattern");
            }
            result.add(tag);
        }
        if (result.size() > maxCount) {
            throw new InvalidInputException("tags", "Size");
        }
        return new ArrayList<>(result);
    }
}
