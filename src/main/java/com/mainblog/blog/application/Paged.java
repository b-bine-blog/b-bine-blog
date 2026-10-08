package com.mainblog.blog.application;

import java.util.List;

/** 번호 페이지 응답(page는 1부터). */
public record Paged<T>(List<T> items, int page, int size, long totalCount) {}
