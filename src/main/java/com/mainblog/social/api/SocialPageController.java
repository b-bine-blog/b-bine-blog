package com.mainblog.social.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 프로필 화면 경로 (003 T019). /profile/{회원 번호} → 정적 profile.html(D-66). 화면 스크립트가 API로 프로필을 읽는다. */
@Controller
public class SocialPageController {

    @GetMapping({"/profile/{userId:[0-9]+}", "/profile/{userId:[0-9]+}/"})
    public String profile() {
        return "forward:/profile.html";
    }
}
