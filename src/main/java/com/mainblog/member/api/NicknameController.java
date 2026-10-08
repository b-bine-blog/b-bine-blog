package com.mainblog.member.api;

import com.mainblog.member.application.NicknamePolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 닉네임 사용 가능 여부 (T031, FR-010, FR-011). 가입 ③단계와 내 정보 수정에서 쓴다. */
@RestController
public class NicknameController {

    private final NicknamePolicy nicknamePolicy;

    public NicknameController(NicknamePolicy nicknamePolicy) {
        this.nicknamePolicy = nicknamePolicy;
    }

    @GetMapping("/api/members/nickname-availability")
    public Availability check(@RequestParam String nickname) {
        NicknamePolicy.Result result = nicknamePolicy.check(nickname.strip());
        return result == NicknamePolicy.Result.AVAILABLE
                ? new Availability(true, null)
                : new Availability(false, result.name());
    }

    public record Availability(boolean available, String reason) {}
}
