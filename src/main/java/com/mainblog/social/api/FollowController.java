package com.mainblog.social.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.social.application.FollowListService;
import com.mainblog.social.application.FollowListService.UserPage;
import com.mainblog.social.application.FollowService;
import com.mainblog.social.application.FollowService.FollowState;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 팔로우·취소와 팔로워·팔로잉 목록 (003 T024·T039, SOC-01, SOC-02, contracts/social-api.yaml follow). 목록은 비회원도 본다. */
@RestController
public class FollowController {

    private final FollowService followService;
    private final FollowListService followListService;

    public FollowController(FollowService followService, FollowListService followListService) {
        this.followService = followService;
        this.followListService = followListService;
    }

    @GetMapping("/api/users/{userId}/followers")
    public UserPage followers(@PathVariable long userId, @RequestParam(required = false) Integer page) {
        return followListService.followers(userId, page);
    }

    @GetMapping("/api/users/{userId}/following")
    public UserPage following(@PathVariable long userId, @RequestParam(required = false) Integer page) {
        return followListService.following(userId, page);
    }

    @PostMapping("/api/users/{userId}/follow")
    public FollowState follow(@PathVariable long userId, @AuthenticationPrincipal AuthenticatedMember member) {
        return followService.follow(member.id(), userId);
    }

    @DeleteMapping("/api/users/{userId}/follow")
    public FollowState unfollow(@PathVariable long userId, @AuthenticationPrincipal AuthenticatedMember member) {
        return followService.unfollow(member.id(), userId);
    }
}
