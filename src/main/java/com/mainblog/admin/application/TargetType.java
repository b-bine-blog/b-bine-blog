package com.mainblog.admin.application;

/** 관리자 조치 대상 종류 (005 T005, FR-039). admin_actions.target_type. */
public enum TargetType {
    USER, BLOG, POST, COMMENT, REPORT, INQUIRY // INQUIRY: 시스템 문의(ADM-08, D-112)
}
