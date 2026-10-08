package com.mainblog.notification.application;

import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 읽음·모두 읽음·삭제 (003 T011, FR-030, FR-031, D-102 #18·#19·#28).
 * 남의 알림 번호면 404. 회원이 지우면 행을 바로 지운다(30일 보관 없음).
 */
@Service
public class NotificationCommandService {

    private final JdbcTemplate jdbc;

    public NotificationCommandService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void read(long userId, long notificationId) {
        requireOwn(userId, notificationId);
        jdbc.update("UPDATE notifications SET is_read = TRUE WHERE id = ? AND recipient_id = ? AND is_read = FALSE",
                notificationId, userId);
    }

    /** 보고 있는 탭의 안 읽은 알림을 모두 읽음으로. 바꾼 수를 돌려준다. */
    @Transactional
    public int readAll(long userId, String tab) {
        NotificationTab t = NotificationTab.parse(tab);
        return jdbc.update("UPDATE notifications SET is_read = TRUE WHERE recipient_id = ? AND is_read = FALSE"
                + t.condition(), userId);
    }

    @Transactional
    public void delete(long userId, long notificationId) {
        if (jdbc.update("DELETE FROM notifications WHERE id = ? AND recipient_id = ?", notificationId, userId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    /** ALL이면 모두, 다른 탭이면 그 탭만 지운다. */
    @Transactional
    public int deleteAll(long userId, String tab) {
        NotificationTab t = NotificationTab.parse(tab);
        return jdbc.update("DELETE FROM notifications WHERE recipient_id = ?" + t.condition(), userId);
    }

    private void requireOwn(long userId, long notificationId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE id = ? AND recipient_id = ?",
                Integer.class, notificationId, userId);
        if (n == null || n == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
