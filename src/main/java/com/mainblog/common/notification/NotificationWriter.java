package com.mainblog.common.notification;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 알림 쓰기 (T013, research R-14). 보기·읽음·폴링은 003(SOC-04).
 * 업무 트랜잭션 안에서 함께 넣어 처리가 되돌려지면 알림도 남지 않는다.
 * (recipient_id, event_key) 유니크와 INSERT IGNORE로 같은 이벤트 알림을 두 번 만들지 않는다.
 */
@Component
public class NotificationWriter {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NotificationWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @param targetType 관련 대상 종류(BLOG, REPORT 등), 없으면 null
     * @param actorId    행동한 회원, 없으면 null
     * @return 새로 만들었으면 true(설정으로 꺼졌거나 이미 있으면 false)
     */
    public boolean write(long recipientId, NotificationType type, String targetType, Long targetId, Long actorId,
                         String content, String eventKey) {
        if (type.settingColumn() != null && !enabled(recipientId, type.settingColumn())) {
            return false;
        }
        String text = content.length() > 300 ? content.substring(0, 300) : content;
        return jdbc.update("INSERT IGNORE INTO notifications (recipient_id, notification_type, tab, target_type, "
                        + "target_id, actor_id, content, event_key, is_read, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?)",
                recipientId, type.name(), type.tab(), targetType, targetId, actorId, text, eventKey,
                LocalDateTime.now(clock)) > 0;
    }

    /** 설정 행이 없으면 모두 켜진 것으로 본다. 컬럼 이름은 NotificationType의 상수에서만 온다. */
    private boolean enabled(long userId, String column) {
        Boolean on = jdbc.query("SELECT " + column + " FROM notification_settings WHERE user_id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : Boolean.TRUE, userId);
        return Boolean.TRUE.equals(on);
    }
}
