package com.mainblog.member.application;

import com.mainblog.common.config.MemberProperties;
import com.mainblog.member.domain.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 닉네임 규칙 (T016, FR-010, FR-011, D-97).
 * 2~12자 한글·영문·숫자, 대소문자 무시 중복 불가, 예약어가 포함되면(대소문자 무시) 불가.
 */
@Component
public class NicknamePolicy {

    private static final Pattern FORMAT = Pattern.compile("^[가-힣a-zA-Z0-9]{2,12}$");

    /** contracts의 nickname-availability 사유와 같다. */
    public enum Result { AVAILABLE, INVALID_FORMAT, RESERVED, DUPLICATE }

    private final List<String> reserved;
    private final UserRepository userRepository;

    public NicknamePolicy(MemberProperties properties, UserRepository userRepository) {
        this.reserved = properties.nickname().reserved().stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        this.userRepository = userRepository;
    }

    /** 형식과 예약어만 본다(DB 조회 없음). */
    public Result checkFormat(String nickname) {
        if (nickname == null || !FORMAT.matcher(nickname).matches()) {
            return Result.INVALID_FORMAT;
        }
        String lower = nickname.toLowerCase(Locale.ROOT);
        for (String word : reserved) {
            if (lower.contains(word)) {
                return Result.RESERVED;
            }
        }
        return Result.AVAILABLE;
    }

    /** 형식·예약어·중복을 모두 본다. */
    public Result check(String nickname) {
        Result format = checkFormat(nickname);
        if (format != Result.AVAILABLE) {
            return format;
        }
        return userRepository.existsByNicknameIgnoreCase(nickname) ? Result.DUPLICATE : Result.AVAILABLE;
    }
}
