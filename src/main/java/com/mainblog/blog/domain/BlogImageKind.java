package com.mainblog.blog.domain;

/** 블로그에 딸린 이미지 종류(images.owner_type). 배경 이미지는 D-121. */
public enum BlogImageKind {
    COVER("BLOG_COVER"),
    BACKGROUND("BLOG_BACKGROUND");

    private final String ownerType;

    BlogImageKind(String ownerType) {
        this.ownerType = ownerType;
    }

    public String ownerType() {
        return ownerType;
    }
}
