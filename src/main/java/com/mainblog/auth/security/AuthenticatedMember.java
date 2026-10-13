package com.mainblog.auth.security;

/** 로그인한 회원. 회원 번호만 담는다. 블로그별 역할은 쓸 때마다 DB에서 본다(헌장 II). */
public record AuthenticatedMember(Long id) {}
