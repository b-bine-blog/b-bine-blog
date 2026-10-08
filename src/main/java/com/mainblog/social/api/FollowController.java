package com.mainblog.social.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.social.application.FollowService;
import com.mainblog.social.application.FollowService.FollowState;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 팔로우·취소 (003 T024, SOC-01, contracts/social-api.yaml follow). */
@RestController
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
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
