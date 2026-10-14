package com.mainblog.board.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.board.application.FeedTagService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 메인 피드 태그 탭 (D-135, api-contracts/board-api.yaml). 로그인한 본인 것만. */
@RestController
public class FeedTagController {

    private final FeedTagService service;

    public FeedTagController(FeedTagService service) {
        this.service = service;
    }

    @GetMapping("/api/me/feed-tags")
    public List<String> list(@AuthenticationPrincipal AuthenticatedMember member) {
        return service.list(member.id());
    }

    @PutMapping("/api/me/feed-tags/{name}")
    public List<String> add(@PathVariable String name, @AuthenticationPrincipal AuthenticatedMember member) {
        return service.add(member.id(), name);
    }

    @DeleteMapping("/api/me/feed-tags/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String name, @AuthenticationPrincipal AuthenticatedMember member) {
        service.remove(member.id(), name);
    }
}
