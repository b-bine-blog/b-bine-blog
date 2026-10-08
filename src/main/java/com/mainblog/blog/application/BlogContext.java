package com.mainblog.blog.application;

import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogMember;

/** 권한 확인을 마친 요청의 블로그와 요청한 회원의 역할. member·userId는 비회원·비멤버면 null. */
public record BlogContext(Blog blog, BlogMember member, Long userId) {

    public Long blogId() {
        return blog.getId();
    }
}
