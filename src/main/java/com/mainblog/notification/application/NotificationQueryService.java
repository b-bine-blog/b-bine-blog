package com.mainblog.notification.application;

import com.mainblog.common.config.NotificationProperties;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 목록·안 읽은 개수 (003 T011, SOC-04, FR-029, FR-032, FR-033, D-102 #17·#20, research R-04·R-05).
 * 받는 사람 본인 것만 읽는다. 목록을 받는 것만으로는 읽음이 되지 않는다(D-102 #18).
 */
@Service
public class NotificationQueryService {

    private final JdbcTemplate jdbc;
    private final NotificationLinkResolver links;
    private final NotificationProperties props;

    public NotificationQueryService(JdbcTemplate jdbc, NotificationLinkResolver links, NotificationProperties props) {
        this.jdbc = jdbc;
        this.links = links;
        this.props = props;
    }

    /** 행동한 회원은 content 안의 닉네임으로만 보인다. actor·이메일 같은 칸은 응답에 없다. */
    public record NotificationItem(long id, String type, String tab, String content, String link, boolean read,
                                   LocalDateTime createdAt) {}

    public record NotificationSlice(List<NotificationItem> items, boolean hasMore, Long nextBefore) {}

    public record UnreadCount(int unreadCount, long pollIntervalSeconds) {}

    private record Row(long id, String type, String tab, String targetType, Long targetId, Long actorId,
                       String content, boolean read, LocalDateTime createdAt) {}

    /** 최신순(id DESC) page-size개. before가 있으면 그 번호보다 오래된 것(더 보기). */
    @Transactional(readOnly = true)
    public NotificationSlice list(long userId, String tab, Long before) {
        NotificationTab t = NotificationTab.parse(tab);
        int size = props.pageSize();
        List<Object> args = new ArrayList<>();
        args.add(userId);
        String where = "recipient_id = ?" + t.condition();
        if (before != null) {
            where += " AND id < ?";
            args.add(before);
        }
        args.add(size + 1);
        List<Row> rows = jdbc.query("SELECT id, notification_type, tab, target_type, target_id, actor_id, content, "
                        + "is_read, created_at FROM notifications WHERE " + where + " ORDER BY id DESC LIMIT ?",
                (rs, i) -> new Row(rs.getLong("id"), rs.getString("notification_type"), rs.getString("tab"),
                        rs.getString("target_type"), rs.getObject("target_id", Long.class),
                        rs.getObject("actor_id", Long.class), rs.getString("content"), rs.getBoolean("is_read"),
                        rs.getObject("created_at", LocalDateTime.class)), args.toArray());
        boolean hasMore = rows.size() > size;
        List<Row> page = hasMore ? rows.subList(0, size) : rows;
        List<NotificationItem> items = page.stream().map(r -> new NotificationItem(r.id(), r.type(),
                NotificationTab.apiValue(r.tab()), r.content(), links.resolve(r.type(), r.targetType(), r.targetId(),
                r.actorId()), r.read(), r.createdAt())).toList();
        Long next = hasMore ? page.get(page.size() - 1).id() : null;
        return new NotificationSlice(items, hasMore, next);
    }

    /** 안 읽은 개수는 badge-max + 1(100)에서 멈춰 센다. 화면은 badge-max(99)를 넘으면 "99+". */
    @Transactional(readOnly = true)
    public UnreadCount unreadCount(long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT 1 FROM notifications WHERE recipient_id = ? "
                + "AND is_read = FALSE LIMIT ?) t", Integer.class, userId, props.badgeMax() + 1);
        return new UnreadCount(n == null ? 0 : n, props.pollInterval().toSeconds());
    }
}
