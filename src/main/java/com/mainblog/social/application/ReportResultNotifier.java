package com.mainblog.social.application;

import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 신고 처리 결과 알림 (003 T036, FR-051, D-38, ADM-04, research R-15). 002 블로그장 처리와 005 관리자 처리가 같이 쓴다.
 * 신고한 회원에게 REPORT_RESOLVED(운영 탭, 설정 report_result)를 한 번 보낸다. 문구에는 대상 종류와 결과만 넣고
 * 신고당한 회원의 닉네임은 넣지 않는다. 신고자가 탈퇴했으면 NotificationWriter가 만들지 않는다.
 */
@Component
public class ReportResultNotifier {

    private final JdbcTemplate jdbc;
    private final NotificationWriter notifications;

    public ReportResultNotifier(JdbcTemplate jdbc, NotificationWriter notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    /** reports.status = DONE, result가 채워진 뒤 부른다. 새로 만들었으면 true. */
    public boolean resolved(long reportId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.reporter_id, r.target_type, r.result, r.blog_id, "
                + "b.name AS blog_name FROM reports r LEFT JOIN blogs b ON b.id = r.blog_id WHERE r.id = ?", reportId);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> r = rows.get(0);
        long reporter = ((Number) r.get("reporter_id")).longValue();
        Long blogId = r.get("blog_id") == null ? null : ((Number) r.get("blog_id")).longValue();
        String where = r.get("blog_name") == null ? "" : "'" + r.get("blog_name") + "' 블로그에 한 ";
        String content = where + targetLabel((String) r.get("target_type")) + " 신고가 처리되었습니다. 결과: "
                + resultLabel((String) r.get("result"));
        return notifications.write(reporter, NotificationType.REPORT_RESOLVED, blogId == null ? "REPORT" : "BLOG",
                blogId == null ? reportId : blogId, null, content, "REPORT_RESOLVED:" + reportId);
    }

    static String targetLabel(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "USER" -> "회원";
            case "BLOG" -> "블로그";
            case "POST" -> "글";
            case "COMMENT" -> "댓글";
            default -> "";
        };
    }

    /** 블로그 결과(NONE·WARNING·SUSPEND·KICK)와 관리자 결과(OWNER_WARNING·OWNER_KICK·BLOG_CLOSE). */
    public static String resultLabel(String result) {
        if (result == null) {
            return "문제 없음";
        }
        return switch (result) {
            case "WARNING" -> "경고";
            case "SUSPEND" -> "정지";
            case "KICK" -> "강제 퇴장";
            case "OWNER_WARNING" -> "블로그장 경고";
            case "OWNER_KICK" -> "블로그장 강퇴";
            case "BLOG_CLOSE" -> "블로그 폐쇄";
            default -> "문제 없음";
        };
    }
}
