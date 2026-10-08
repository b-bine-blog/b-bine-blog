package com.mainblog.blog.application;

/** 블로그 권한 이름 (T014, research R-01). 누가 무엇을 갖는지는 data-model.md "권한표". */
public enum BlogPermission {
    /** 블로그 열람. 공개 범위에 따름. */
    VIEW,
    /** 멤버 기능(블로그 탈퇴, 위임 응답). */
    MEMBER,
    /** 이름·소개·대표 이미지·태그 수정. */
    BLOG_INFO,
    /** 참여 승인·거절, 제재, 신고 처리, 해제 문의, 멤버 정보, 블랙리스트 열람. */
    MEMBER_MANAGE,
    /** 다른 멤버 글 삭제, 카테고리, 블로그 공지(004). */
    POST_MANAGE,
    /** 블로그장만: 공개 범위·참여 방식, 부블로그장, 위임, 폐쇄·철회, 공유 링크 다시 만들기. */
    OWNER
}
