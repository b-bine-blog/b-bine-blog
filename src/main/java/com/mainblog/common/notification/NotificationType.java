package com.mainblog.common.notification;

/**
 * 알림 종류 (T013, 3.6, SOC-04). tab과 끌 수 있는지(설정 컬럼)를 함께 정한다. 설정 컬럼이 null이면 끌 수 없다.
 * 002가 쓰는 것은 specs/002-blog-management/data-model.md "알림 종류". 003·004가 종류를 더한다.
 */
public enum NotificationType {
    JOIN_REQUESTED("BLOG", "join_request"),
    JOIN_APPROVED("BLOG", "join_result"),
    JOIN_REJECTED("BLOG", "join_result"),
    BLOG_PRIVATE("BLOG", null),
    MEMBER_WARNED("OPERATION", null),
    MEMBER_SUSPENDED("OPERATION", null),
    MEMBER_UNSUSPENDED("OPERATION", null),
    MEMBER_KICKED("OPERATION", null),
    REPORT_RESOLVED("OPERATION", "report_result"),
    APPEAL_RELEASED("OPERATION", null),
    APPEAL_REJECTED("OPERATION", null),
    DELEGATION_REQUESTED("OPERATION", null),
    DELEGATION_ACCEPTED("OPERATION", null),
    DELEGATION_REJECTED("OPERATION", null),
    DELEGATION_CANCELED("OPERATION", null),
    BLOG_CLOSING("BLOG", null),
    BLOG_CLOSING_WITHDRAWN("BLOG", null),
    BLOG_CLOSED_REQUEST_REJECTED("BLOG", null);

    private final String tab;
    private final String settingColumn;

    NotificationType(String tab, String settingColumn) {
        this.tab = tab;
        this.settingColumn = settingColumn;
    }

    public String tab() {
        return tab;
    }

    /** notification_settings의 컬럼 이름. 끌 수 없는 알림은 null. */
    public String settingColumn() {
        return settingColumn;
    }
}
