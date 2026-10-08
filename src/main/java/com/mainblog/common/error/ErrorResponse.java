package com.mainblog.common.error;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 오류 응답 본문 — contracts의 {@code Error} 스키마(code, message, fieldErrors).
 * 로그인 오류의 captchaRequired·retryAfterSeconds, 인증번호 오류의 남은 횟수 같은 추가 값은 {@code extras}로 펼쳐 넣는다.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors,
                            @JsonIgnore Map<String, Object> extras) {

    public record FieldError(String field, String code) {}

    public static ErrorResponse of(ErrorCode errorCode, Map<String, Object> extras) {
        return new ErrorResponse(errorCode.name(), errorCode.message(), List.of(), extras);
    }

    public static ErrorResponse validation(List<FieldError> fieldErrors) {
        ErrorCode c = ErrorCode.VALIDATION_FAILED;
        return new ErrorResponse(c.name(), c.message(), fieldErrors, Map.of());
    }

    @JsonAnyGetter
    public Map<String, Object> anyExtras() {
        return extras == null ? Map.of() : extras;
    }
}
