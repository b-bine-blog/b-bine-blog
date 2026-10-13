package com.mainblog.member.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.mainblog.auth.application.TokenService.IssuedTokens;
import com.mainblog.auth.security.AuthCookies;
import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.member.application.MemberService;
import com.mainblog.member.application.MemberService.MyInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내 정보 API (T063). 계약: contracts의 me 태그.
 * 회원 번호는 로그인 정보(principal)에서만 받고 경로·본문으로는 받지 않는다(US5 인수 시나리오 6).
 */
@RestController
@RequestMapping("/api/members/me")
public class MemberController {

    /** PATCH로 바꿀 수 있는 항목. 이메일·이름 등 다른 필드가 오면 400(FR-041). */
    private static final Set<String> EDITABLE = Set.of("nickname", "phone");
    private static final int MAX_FIELD_LENGTH = 100;

    private final MemberService memberService;
    private final AuthCookies cookies;

    public MemberController(MemberService memberService, AuthCookies cookies) {
        this.memberService = memberService;
        this.cookies = cookies;
    }

    @GetMapping
    public MyInfo myInfo(@AuthenticationPrincipal AuthenticatedMember member) {
        return memberService.myInfo(member.id());
    }

    @PatchMapping
    public MyInfo update(@AuthenticationPrincipal AuthenticatedMember member, @RequestBody JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new InvalidInputException("body", "Object");
        }
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!EDITABLE.contains(name)) {
                throw new InvalidInputException(name, "NotAllowed");
            }
        }
        return memberService.update(member.id(), text(body, "nickname"), text(body, "phone"));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedMember member,
                                               @Valid @RequestBody PasswordChangeRequest request,
                                               HttpServletRequest httpRequest, HttpServletResponse response) {
        IssuedTokens tokens = memberService.changePassword(member.id(), request.currentPassword(),
                request.newPassword(), request.newPasswordConfirm(),
                AuthCookies.read(httpRequest, AuthCookies.REFRESH));
        cookies.write(response, tokens.accessToken(), tokens.refreshToken(), tokens.rememberMe(),
                tokens.refreshRemaining());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/profile-image")
    public ProfileImageResponse changeProfileImage(@AuthenticationPrincipal AuthenticatedMember member,
                                                   @RequestParam("file") MultipartFile file) throws IOException {
        return new ProfileImageResponse(memberService.changeProfileImage(member.id(), file.getBytes(),
                file.getOriginalFilename(), file.getContentType()));
    }

    @DeleteMapping("/profile-image")
    public ResponseEntity<Void> deleteProfileImage(@AuthenticationPrincipal AuthenticatedMember member) {
        memberService.deleteProfileImage(member.id());
        return ResponseEntity.noContent().build();
    }

    /** 없거나 null이면 그대로 둔다. 문자열이 아니면 400. */
    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.asText().length() > MAX_FIELD_LENGTH) {
            throw new InvalidInputException(field, "Pattern");
        }
        return node.asText();
    }

    public record PasswordChangeRequest(@Size(max = 100) String currentPassword, // 비밀번호 없는 소셜 회원은 비움(008)
                                        @NotNull @Size(max = 100) String newPassword,
                                        @NotNull @Size(max = 100) String newPasswordConfirm) {}

    public record ProfileImageResponse(String profileImageUrl) {}
}
