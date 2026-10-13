package com.mainblog.board.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 공지·검색·태그 화면 경로 (004 T045·T053, D-66). 상세(/notices/{번호})는 {@link PostOgPageController}. */
@Controller
public class NoticePageController {

    @GetMapping({"/notices", "/notices/"})
    public String notices() {
        return "forward:/notices/index.html";
    }

    @GetMapping({"/notices/write", "/notices/{postId:\\d+}/edit"})
    public String write() {
        return "forward:/notices/write.html";
    }

    @GetMapping({"/search", "/search/"})
    public String search() {
        return "forward:/search.html";
    }

    @GetMapping("/tags/{name}")
    public String tag() {
        return "forward:/tags/index.html";
    }
}
