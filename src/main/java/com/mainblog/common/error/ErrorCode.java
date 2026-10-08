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
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "올릴 수 없는 파일 형식입니다."),

    // 002 블로그 관리 (contracts/blog-api.yaml)
    BLOG_NOT_FOUND(HttpStatus.NOT_FOUND, "블로그를 찾을 수 없습니다."),
    BLOG_FORBIDDEN(HttpStatus.FORBIDDEN, "이 블로그에서 할 수 없는 일입니다."),
    BLOG_SUSPENDED(HttpStatus.FORBIDDEN, "이 블로그에서 정지된 상태입니다."),
    ADMIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "메인 관리자 계정은 블로그 활동을 할 수 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다."),
    BLOG_ADDRESS_TAKEN(HttpStatus.CONFLICT, "이미 쓰고 있는 블로그 주소입니다."),
    BLOG_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "만들 수 있는 블로그 개수를 넘었습니다."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 참여한 블로그입니다."),
    ALREADY_OWNER(HttpStatus.CONFLICT, "내가 운영하는 블로그입니다."),
    JOIN_PENDING_EXISTS(HttpStatus.CONFLICT, "이미 참여 신청을 했습니다. 처리를 기다려 주세요."),
    JOIN_REAPPLY_TOO_SOON(HttpStatus.CONFLICT, "거절된 뒤 7일이 지나야 다시 신청할 수 있습니다."),
    JOIN_NOT_PENDING(HttpStatus.CONFLICT, "이미 처리된 신청입니다."),
    BLOG_BLACKLISTED(HttpStatus.FORBIDDEN, "이 블로그에는 참여 신청을 할 수 없습니다. 문의하기를 남길 수 있습니다."),
    OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "블로그장은 위임하기 전에는 블로그를 탈퇴할 수 없습니다."),
    NOT_SUSPENDED(HttpStatus.CONFLICT, "정지 중이 아닙니다."),
    ALREADY_SUSPENDED(HttpStatus.CONFLICT, "이미 정지 중입니다."),
    REPORT_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 처리한 신고입니다."),
    NOT_BLACKLISTED(HttpStatus.CONFLICT, "이 블로그의 블랙리스트에 있지 않습니다."),
    APPEAL_PENDING_EXISTS(HttpStatus.CONFLICT, "이미 문의를 남겼습니다. 처리를 기다려 주세요."),
    APPEAL_RETRY_TOO_SOON(HttpStatus.CONFLICT, "거절된 뒤 7일이 지나야 다시 문의할 수 있습니다."),
    APPEAL_NOT_PENDING(HttpStatus.CONFLICT, "이미 처리된 문의입니다."),
    DELEGATION_PENDING_EXISTS(HttpStatus.CONFLICT, "대기 중인 위임 요청이 있습니다."),
    DELEGATION_NOT_PENDING(HttpStatus.CONFLICT, "대기 중인 위임 요청이 없습니다."),
    BLOG_CLOSING(HttpStatus.CONFLICT, "폐쇄 예정인 블로그는 위임할 수 없습니다. 먼저 폐쇄를 철회해 주세요."),
    BLOG_ALREADY_CLOSING(HttpStatus.CONFLICT, "이미 폐쇄 예정입니다."),
    BLOG_NOT_CLOSING(HttpStatus.CONFLICT, "폐쇄 예정이 아닙니다.");

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
