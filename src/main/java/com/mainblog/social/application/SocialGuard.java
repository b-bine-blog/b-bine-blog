package com.mainblog.social.application;

import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 소셜 요청의 공통 확인 (003 T006, D-90, research 가정 1). 요청마다 users·user_blocks를 DB에서 읽는다(SEC-07).
 * - 로그인 회원이 메인 관리자면 403 ADMIN_NOT_ALLOWED
 * - 대상 회원이 없거나 탈퇴했거나 메인 관리자면 404 NOT_FOUND
 * - 두 회원 사이 차단(어느 방향이든)
 */
@Component
public class SocialGuard {

    private final JdbcTemplate jdbc;

    public SocialGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 프로필에 쓰는 회원 칸. 이메일·이름·전화번호는 읽지 않는다(D-22). */
    public record UserRow(long id, String nickname, String bio, String role, String status) {

        public boolean admin() {
            return "ADMIN".equals(role);
        }

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    public Optional<UserRow> find(long userId) {
        return jdbc.query("SELECT id, nickname, bio, role, status FROM users WHERE id = ?",
                (rs, i) -> new UserRow(rs.getLong("id"), rs.getString("nickname"), rs.getString("bio"),
                        rs.getString("role"), rs.getString("status")), userId).stream().findFirst();
    }

    /** 로그인 회원이 메인 관리자면 403(D-90). 탈퇴해 남은 토큰이면 401. */
    public UserRow requireActor(long userId) {
        UserRow me = find(userId).filter(UserRow::active)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (me.admin()) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_ALLOWED);
        }
        return me;
    }

    /** 소셜 대상이 될 수 있는 회원(활동 중인 일반 회원). 아니면 404. */
    public UserRow requireTarget(long userId) {
        return find(userId).filter(u -> u.active() && !u.admin())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** blocker가 blocked를 차단했는지. */
    public boolean blocks(long blockerId, long blockedId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM user_blocks WHERE blocker_id = ? AND blocked_id = ?",
                Integer.class, blockerId, blockedId);
        return n != null && n > 0;
    }

    /** 둘 사이에 어느 방향이든 차단이 있는지(D-102 #11). */
    public boolean blockedEitherWay(long a, long b) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM user_blocks WHERE (blocker_id = ? AND blocked_id = ?) "
                + "OR (blocker_id = ? AND blocked_id = ?)", Integer.class, a, b, b, a);
        return n != null && n > 0;
    }
}
