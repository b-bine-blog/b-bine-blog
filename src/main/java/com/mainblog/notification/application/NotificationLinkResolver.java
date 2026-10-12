package com.mainblog.notification.application;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * 알림을 눌렀을 때 이동할 곳 (003 T010, FR-033, D-102 #21, research R-06).
 * 서버가 만든 상대 경로만 준다. 대상(글·댓글·블로그·회원)이 없어졌으면 null이고 화면은 "볼 수 없는 내용입니다"를 보여준다.
 * 볼 권한은 이동한 화면이 부르는 API가 다시 확인한다(002 BlogPermissionService, 004 PostPolicy).
 */
@Component
public class NotificationLinkResolver {

    private final JdbcTemplate jdbc;

    public NotificationLinkResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String resolve(String type, String targetType, Long targetId, Long actorId) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "POST_COMMENT", "COMMENT_REPLY" -> "COMMENT".equals(targetType) && targetId != null
                    ? commentLink(targetId) : null;
            case "POST_LIKE" -> "POST".equals(targetType) && targetId != null ? blogPostLink(targetId) : null;
            case "NOTICE" -> "POST".equals(targetType) && targetId != null ? noticeLink(targetId) : null;
            case "NEW_FOLLOWER" -> actorId != null && activeUser(actorId) ? "/profile/" + actorId : null;
            case "BLOG_SUBSCRIBED", "BLOG_CLOSING", "BLOG_CLOSING_WITHDRAWN", "BLOG_PRIVATE", "DELEGATION_REQUESTED" ->
                    blogHome(targetType, targetId, "");
            case "JOIN_REQUESTED" -> blogHome(targetType, targetId, "/manage?tab=requests");
            case "INQUIRY_RECEIVED" -> "/admin#inquiries"; // ADM-08, D-112
            case "INQUIRY_ANSWERED" -> "/inquiry.html#mine";
            default -> null;
        };
    }

    private String commentLink(long commentId) {
        List<Long> post = jdbc.queryForList("SELECT post_id FROM comments WHERE id = ? AND status = 'VISIBLE'",
                Long.class, commentId);
        return post.isEmpty() ? null : blogPostLink(post.get(0));
    }

    private String blogPostLink(long postId) {
        List<String> address = jdbc.queryForList("SELECT b.address FROM posts p JOIN blogs b ON b.id = p.blog_id "
                + "WHERE p.id = ? AND p.status = 'VISIBLE' AND p.board_type = 'BLOG' AND b.status <> 'CLOSED' "
                + "AND b.address IS NOT NULL", String.class, postId);
        return address.isEmpty() ? null : "/blog/" + encode(address.get(0)) + "/posts/" + postId;
    }

    private String noticeLink(long postId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id = ? AND status = 'VISIBLE' "
                + "AND board_type = 'NOTICE'", Integer.class, postId);
        return n != null && n > 0 ? "/notices/" + postId : null;
    }

    private String blogHome(String targetType, Long blogId, String suffix) {
        if (!"BLOG".equals(targetType) || blogId == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT address FROM blogs WHERE id = ? "
                + "AND status <> 'CLOSED' AND address IS NOT NULL", blogId);
        return rows.isEmpty() ? null : "/blog/" + encode((String) rows.get(0).get("address")) + suffix;
    }

    private boolean activeUser(long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND status = 'ACTIVE'",
                Integer.class, userId);
        return n != null && n > 0;
    }

    private static String encode(String segment) {
        return UriUtils.encodePathSegment(segment, java.nio.charset.StandardCharsets.UTF_8);
    }
}
