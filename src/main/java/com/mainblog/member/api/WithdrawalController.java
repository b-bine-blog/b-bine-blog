package com.mainblog.member.api;

import com.mainblog.auth.security.AuthCookies;
import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.common.config.ClockConfig;
import com.mainblog.member.application.WithdrawalService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 탈퇴 API (T049). 회원 번호는 로그인 정보에서만 받는다. */
@RestController
@RequestMapping("/api/members/me/withdrawal")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final AuthCookies cookies;

    public WithdrawalController(WithdrawalService withdrawalService, AuthCookies cookies) {
        this.withdrawalService = withdrawalService;
        this.cookies = cookies;
    }

    @GetMapping
    public WithdrawalStatus status(@AuthenticationPrincipal AuthenticatedMember member) {
        List<OwnedBlogView> blogs = withdrawalService.ownedBlogs(member.id()).stream()
                .map(b -> new OwnedBlogView(b.blogId(), b.name(), b.address(), b.status(),
                        b.closeScheduledAt() == null ? null
                                : b.closeScheduledAt().atZone(ClockConfig.ZONE).toOffsetDateTime()))
                .toList();
        return new WithdrawalStatus(blogs.isEmpty(), blogs);
    }

    @PostMapping
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthenticatedMember member,
                                         @Valid @RequestBody WithdrawalRequest request, HttpServletResponse response) {
        withdrawalService.withdraw(member.id(), request.password());
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    public record WithdrawalRequest(@NotNull String password) {}

    public record WithdrawalStatus(boolean canWithdraw, List<OwnedBlogView> ownedBlogs) {}

    public record OwnedBlogView(Long blogId, String name, String address, String status,
                                OffsetDateTime closeScheduledAt) {}
}
