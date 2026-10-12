package com.mainblog.admin.application;

/**
 * 관리자 활동 기록 종류 (005 T005, FR-039, research R-06). admin_actions.action_type에 이름 그대로 저장한다.
 * 스키마 설명의 13개에 숨김 해제 3개(BLOG_UNHIDE·POST_UNHIDE·COMMENT_UNHIDE, D-103 #7·#10)를 더했다.
 */
public enum ActionType {
    OWNER_WARNING,
    OWNER_KICK,
    AUTO_DELEGATE,
    CLOSE_SCHEDULE,
    BLOG_HIDE,
    BLOG_UNHIDE,
    BLOG_FORCE_CLOSE,
    POST_HIDE,
    POST_UNHIDE,
    POST_DELETE,
    COMMENT_HIDE,
    COMMENT_UNHIDE,
    COMMENT_DELETE,
    RESTORE,
    REPORT_HANDLE,
    PRIVACY_VIEW,
    INQUIRY_ANSWER // 시스템 문의 답변(ADM-08, D-112)
}
