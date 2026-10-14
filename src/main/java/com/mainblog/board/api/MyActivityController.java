package com.mainblog.board.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.board.application.BoardViews.PostPage;
import com.mainblog.board.application.MyActivityService;
import com.mainblog.board.application.MyActivityService.MyCommentPage;
import com.mainblog.board.application.MyActivityService.ScrapResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 계정 메뉴의 작성글·작성댓글·스크랩 (D-134, api-contracts/board-api.yaml). 로그인한 본인 것만. */
@RestController
public class MyActivityController {

    private final MyActivityService service;

    public MyActivityController(MyActivityService service) {
        this.service = service;
    }

    @PutMapping("/api/posts/{postId}/scrap")
    public ScrapResult scrap(@PathVariable long postId, @AuthenticationPrincipal AuthenticatedMember member) {
        return service.toggleScrap(postId, member.id());
    }

    @GetMapping("/api/me/scraps")
    public PostPage scraps(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                           @AuthenticationPrincipal AuthenticatedMember member) {
        return service.scraps(member.id(), page, size);
    }

    @GetMapping("/api/me/posts")
    public PostPage posts(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                          @AuthenticationPrincipal AuthenticatedMember member) {
        return service.posts(member.id(), page, size);
    }

    @GetMapping("/api/me/comments")
    public MyCommentPage comments(@RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size,
                                  @AuthenticationPrincipal AuthenticatedMember member) {
        return service.comments(member.id(), page, size);
    }
}
