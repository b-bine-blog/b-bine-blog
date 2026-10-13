package com.mainblog.common.error;

/** 서버에서 검사한 입력값 오류. 400 VALIDATION_FAILED와 fieldErrors 한 건으로 응답한다. */
public class InvalidInputException extends RuntimeException {

    private final String field;
    private final String code;

    public InvalidInputException(String field, String code) {
        super(field + ":" + code);
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public String code() {
        return code;
    }
}
