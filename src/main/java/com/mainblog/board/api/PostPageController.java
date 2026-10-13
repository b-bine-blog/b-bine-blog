package com.mainblog.board.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 게시판 화면 경로 (004 T020, D-66, D-70). 글쓰기·수정은 정적 HTML로 넘긴다.
 * 글 상세(/blog/{주소}/posts/{번호})는 og 태그를 채워야 해서 {@link PostOgPageController}가 맡는다.
 */
@Controller
public class PostPageController {

    @GetMapping({"/blog/{address:[a-z0-9-]+}/write", "/blog/{address:[a-z0-9-]+}/posts/{postId:\\d+}/edit"})
    public String write() {
        return "forward:/blog/write.html";
    }
}
