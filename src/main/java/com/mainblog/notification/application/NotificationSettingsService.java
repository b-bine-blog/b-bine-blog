package com.mainblog.notification.application;

import com.mainblog.common.config.NotificationProperties;
import com.mainblog.common.error.InvalidInputException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 설정 (003 T030, SOC-04, FR-054, FR-055, FR-057, FR-058, D-102 #26·#27·#29, research R-08).
 * 끌 수 있는 9가지와 보관 기간(7·30일)만 받는다. 행이 없으면 모두 켜짐·30일이다. 블로그 단위 설정은 없다.
 * 끄면 그 뒤 새 알림만 만들지 않고(NotificationWriter), 받은 알림은 그대로 둔다.
 */
@Service
public class NotificationSettingsService {

    /** API 키 → notification_settings 컬럼. 이 표에 있는 이름만 SQL에 들어간다. */
    private static final Map<String, String> SWITCHES = new LinkedHashMap<>();

    static {
        SWITCHES.put("postComment", "post_comment");
        SWITCHES.put("commentReply", "comment_reply");
        SWITCHES.put("postLike", "post_like");
        SWITCHES.put("newFollower", "new_follower");
        SWITCHES.put("blogSubscribed", "blog_subscribed");
        SWITCHES.put("joinRequest", "join_request");
        SWITCHES.put("joinResult", "join_result");
        SWITCHES.put("reportResult", "report_result");
        SWITCHES.put("notice", "notice");
    }

    private static final String RETENTION = "retentionDays";

    private final JdbcTemplate jdbc;
    private final NotificationProperties props;

    public NotificationSettingsService(JdbcTemplate jdbc, NotificationProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + String.join(", ", SWITCHES.values())
                + ", retention_days FROM notification_settings WHERE user_id = ?", userId);
        Map<String, Object> row = rows.isEmpty() ? null : rows.get(0);
        SWITCHES.forEach((key, column) -> out.put(key, row == null || toBoolean(row.get(column))));
        out.put(RETENTION, row == null ? props.retention().defaultDays() : ((Number) row.get("retention_days")).intValue());
        return out;
    }

    /** 보낸 항목만 바꾼다. 다른 키(끌 수 없는 알림 등)는 400 NotAllowed. */
    @Transactional
    public Map<String, Object> update(long userId, Map<String, Object> body) {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (SWITCHES.containsKey(key)) {
                if (!(value instanceof Boolean)) {
                    throw new InvalidInputException(key, "Type");
                }
                columns.add(SWITCHES.get(key));
                values.add(value);
            } else if (RETENTION.equals(key)) {
                if (!(value instanceof Integer days) || !props.retention().allows(days)) {
                    throw new InvalidInputException(key, "Allowed");
                }
                columns.add("retention_days");
                values.add(days);
            } else {
                throw new InvalidInputException(key, "NotAllowed");
            }
        }
        if (!columns.isEmpty()) {
            List<Object> args = new ArrayList<>();
            args.add(userId);
            args.addAll(values);
            String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
            String updates = String.join(", ", columns.stream().map(c -> c + " = VALUES(" + c + ")").toList());
            jdbc.update("INSERT INTO notification_settings (user_id, " + String.join(", ", columns) + ") VALUES (?, "
                    + placeholders + ") ON DUPLICATE KEY UPDATE " + updates, args.toArray());
        }
        return get(userId);
    }

    private static boolean toBoolean(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        return v instanceof Number n && n.intValue() != 0;
    }
}
