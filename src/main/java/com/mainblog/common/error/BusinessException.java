package com.mainblog.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

/** 업무 규칙 위반. {@link GlobalExceptionHandler}가 {@link ErrorCode}의 HTTP 상태와 {@link ErrorResponse}로 바꾼다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> extras = new LinkedHashMap<>();

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    /** 응답 본문에 더할 값. 예: {@code with("captchaRequired", true)}. null 값은 넣지 않는다. */
    public BusinessException with(String key, Object value) {
        if (value != null) {
            extras.put(key, value);
        }
        return this;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> extras() {
        return Map.copyOf(extras);
    }
}
