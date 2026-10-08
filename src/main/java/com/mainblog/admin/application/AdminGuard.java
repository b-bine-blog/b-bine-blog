package com.mainblog.admin.application;

import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 메인 관리자 확인 (005 T007, FR-001~FR-003, SEC-11, research R-02).
 * 주소 단위 확인(/api/admin/** hasAuthority)에 더해 서비스에서도 DB의 role을 한 번 더 본다. 주소 규칙을 실수로 빼도 막히게 하기 위해서다.
 */
@Component
public class AdminGuard {

    private final JdbcTemplate jdbc;

    public AdminGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 지금 ACTIVE인 메인 관리자가 아니면 403 ADMIN_ONLY. 응답에 회원 정보는 담지 않는다(D-28). */
    public void require(Long userId) {
        if (!isAdmin(userId)) {
            throw new BusinessException(ErrorCode.ADMIN_ONLY);
        }
    }

    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        Boolean admin = jdbc.query("SELECT role = 'ADMIN' AND status = 'ACTIVE' FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, userId);
        return Boolean.TRUE.equals(admin);
    }
}
