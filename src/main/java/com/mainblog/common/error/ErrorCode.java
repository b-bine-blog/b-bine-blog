package com.mainblog.common.error;

import org.springframework.http.HttpStatus;

/** API 오류 코드와 HTTP 상태 (T008). contracts/member-auth-api.yaml의 응답 코드와 같다. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해 주세요."),
    CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 맞지 않습니다."),
    CODE_INVALID(HttpStatus.BAD_REQUEST, "인증번호가 만료되었거나 더 이상 쓸 수 없습니다. 다시 받아 주세요."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증을 다시 해 주세요."),
    NICKNAME_DUPLICATE(HttpStatus.CONFLICT, "이미 쓰고 있는 닉네임입니다."),
    SIGNUP_UNAVAILABLE(HttpStatus.CONFLICT, "가입을 끝낼 수 없습니다. 처음부터 다시 해 주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않습니다"),
    LOCKED(HttpStatus.LOCKED, "로그인을 5번 잘못해 잠시 잠겼습니다. 잠시 뒤 다시 시도해 주세요."),
    CAPTCHA_REQUIRED(HttpStatus.BAD_REQUEST, "사람 확인을 해 주세요."),
    CAPTCHA_FAILED(HttpStatus.BAD_REQUEST, "사람 확인에 실패했습니다. 다시 해 주세요."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 뒤 다시 시도해 주세요."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.GONE, "시간이 지났습니다. 이메일 찾기를 다시 해 주세요."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.FORBIDDEN, "현재 비밀번호가 맞지 않습니다."),
    PASSWORD_MISMATCH(HttpStatus.FORBIDDEN, "비밀번호가 맞지 않습니다."),
    OWNS_BLOGS(HttpStatus.CONFLICT, "블로그장인 블로그가 있어 탈퇴할 수 없습니다. 위임하거나 폐쇄해 주세요."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일이 너무 큽니다."),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "올릴 수 없는 파일 형식입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
