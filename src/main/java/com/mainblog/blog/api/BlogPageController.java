package com.mainblog.blog.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 블로그 화면 경로 (T017, FR-005, D-70, research R-03). /blog/{주소}는 블로그 홈, /blog/{주소}/manage는 운영 화면.
 * 화면은 정적 HTML이고(D-66), 주소로 블로그를 찾는 것은 화면 스크립트가 API로 한다.
 */
@Controller
public class BlogPageController {

    @GetMapping({"/blog/{address:[a-z0-9-]+}", "/blog/{address:[a-z0-9-]+}/"})
    public String home() {
        return "forward:/blog/index.html";
    }

    @GetMapping("/blog/{address:[a-z0-9-]+}/manage")
    public String manage() {
        return "forward:/blog/manage.html";
    }
}
