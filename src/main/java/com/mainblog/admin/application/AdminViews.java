package com.mainblog.admin.application;

import java.util.List;

/**
 * 관리자 응답 공통 모양 (005, contracts/admin-api.yaml components.schemas).
 * 다른 회원은 닉네임만 담는다. 탈퇴한 회원은 "탈퇴한 회원"(D-103 #3·#19).
 */
public final class AdminViews {

    public static final String WITHDRAWN_NAME = "탈퇴한 회원";

    private AdminViews() {
    }

    /** contracts UserRef. */
    public record UserRef(long id, String nickname, boolean withdrawn) {

        /** users.status와 nickname으로 만든다. id가 null이면 null. */
        public static UserRef of(Long id, String nickname, String status) {
            if (id == null) {
                return null;
            }
            boolean withdrawn = !"ACTIVE".equals(status);
            return new UserRef(id, withdrawn ? WITHDRAWN_NAME : nickname, withdrawn);
        }
    }

    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

        public static <T> Page<T> of(List<T> items, int page, int size, long total) {
            return new Page<>(items, page, size, total, (int) ((total + size - 1) / size));
        }
    }
}
