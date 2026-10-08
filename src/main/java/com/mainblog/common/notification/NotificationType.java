package com.mainblog.common.notification;

/**
 * 알림 종류 (T013, 3.6, SOC-04). tab과 끌 수 있는지(설정 컬럼)를 함께 정한다. 설정 컬럼이 null이면 끌 수 없다.
 * 002가 쓰는 것은 specs/002-blog-management/data-model.md "알림 종류". 003·004가 종류를 더한다.
 * 위임 요청·결과와 블랙리스트 해제 문의 결과는 3.6 표대로 "블로그" 탭(003 T015, FR-043, FR-044, FR-049, research R-03).
 * blockable: 받는 사람이 행동한 회원을 차단했으면 만들지 않는 종류(003 research R-07, D-102 #11).
 */
public enum NotificationType {
    JOIN_REQUESTED("BLOG", "join_request", true),
    JOIN_APPROVED("BLOG", "join_result"),
    JOIN_REJECTED("BLOG", "join_result"),
    BLOG_PRIVATE("BLOG", null),
    MEMBER_WARNED("OPERATION", null),
    MEMBER_SUSPENDED("OPERATION", null),
    MEMBER_UNSUSPENDED("OPERATION", null),
    MEMBER_KICKED("OPERATION", null),
    REPORT_RESOLVED("OPERATION", "report_result"),
    APPEAL_RELEASED("BLOG", null),
    APPEAL_REJECTED("BLOG", null),
    DELEGATION_REQUESTED("BLOG", null),
    DELEGATION_ACCEPTED("BLOG", null),
    DELEGATION_REJECTED("BLOG", null),
    DELEGATION_CANCELED("BLOG", null),
    BLOG_CLOSING("BLOG", null),
    BLOG_CLOSING_WITHDRAWN("BLOG", null),
    BLOG_CLOSED_REQUEST_REJECTED("BLOG", null),

    // 004 게시판 (specs/004-board/data-model.md "알림 종류")
    POST_COMMENT("COMMENT", "post_comment", true),
    COMMENT_REPLY("COMMENT", "comment_reply", true),
    POST_LIKE("LIKE", "post_like", true),
    POST_DELETED("OPERATION", null),
    NOTICE("OPERATION", "notice"),

    // 003 소셜 (specs/003-social-notification/data-model.md "알림 종류"). 화면의 팔로우·구독 탭은 FOLLOW·SUBSCRIBE를 함께 보여준다
    NEW_FOLLOWER("FOLLOW", "new_follower", true),
    BLOG_SUBSCRIBED("SUBSCRIBE", "blog_subscribed", true);

    private final String tab;
    private final String settingColumn;
    private final boolean blockable;

    NotificationType(String tab, String settingColumn) {
        this(tab, settingColumn, false);
    }

    NotificationType(String tab, String settingColumn, boolean blockable) {
        this.tab = tab;
        this.settingColumn = settingColumn;
        this.blockable = blockable;
    }

    /** 받는 사람이 행동한 회원을 차단했으면 만들지 않는 종류인지(D-102 #11). 끌 수 없는 운영 알림은 false. */
    public boolean blockable() {
        return blockable;
    }

    public String tab() {
        return tab;
    }

    /** notification_settings의 컬럼 이름. 끌 수 없는 알림은 null. */
    public String settingColumn() {
        return settingColumn;
    }
}
