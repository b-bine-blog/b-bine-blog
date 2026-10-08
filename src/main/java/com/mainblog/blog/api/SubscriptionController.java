package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.BlogViews.BlogSummary;
import com.mainblog.blog.application.SubscriptionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 구독하기·취소·내 구독 목록 (T043, SOC-01, FR-029). */
@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/api/blogs/{blogId}/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        subscriptionService.subscribe(blogId, member.id());
    }

    @DeleteMapping("/api/blogs/{blogId}/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        subscriptionService.unsubscribe(blogId, member.id());
    }

    @GetMapping("/api/me/subscriptions")
    public Items mySubscriptions(@AuthenticationPrincipal AuthenticatedMember member) {
        return new Items(subscriptionService.mySubscriptions(member.id()));
    }

    public record Items(List<BlogSummary> items) {}
}
