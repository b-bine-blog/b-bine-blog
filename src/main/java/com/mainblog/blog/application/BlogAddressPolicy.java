package com.mainblog.blog.application;

import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.InvalidInputException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 블로그 주소 규칙 (T022, FR-003, FR-004, 6.5, research R-03).
 * 영문 소문자·숫자·- 3~30자, 예약어 불가. 중복은 DB 유니크 제약이 막는다(BlogService).
 */
@Component
public class BlogAddressPolicy {

    private static final Pattern ADDRESS = Pattern.compile("^[a-z0-9-]{3,30}$");

    private final Set<String> reserved;

    public BlogAddressPolicy(BlogProperties properties) {
        this.reserved = properties.address().reserved().stream()
                .map(w -> w.strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 맞지 않으면 400(field = "address", code = Pattern 또는 Reserved). */
    public String check(String raw) {
        String address = raw == null ? "" : raw.strip();
        if (!ADDRESS.matcher(address).matches()) {
            throw new InvalidInputException("address", "Pattern");
        }
        if (reserved.contains(address)) {
            throw new InvalidInputException("address", "Reserved");
        }
        return address;
    }
}
