package com.mainblog.social.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.social.application.ProfileService;
import com.mainblog.social.application.ProfileService.Bio;
import com.mainblog.social.application.ProfileService.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 회원 프로필 보기(비회원 포함)와 내 소개 고치기 (003 T018, SOC-03, contracts/social-api.yaml profile). */
@RestController
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/api/users/{userId}/profile")
    public Profile profile(@PathVariable long userId, @AuthenticationPrincipal AuthenticatedMember member) {
        return profileService.profile(userId, member == null ? null : member.id());
    }

    /** 회원 번호는 로그인 정보로만 정한다. 남의 소개를 고치는 경로는 없다. */
    @PutMapping("/api/members/me/bio")
    public Bio updateBio(@RequestBody Bio request, @AuthenticationPrincipal AuthenticatedMember member) {
        return profileService.updateBio(member.id(), request.bio());
    }
}
