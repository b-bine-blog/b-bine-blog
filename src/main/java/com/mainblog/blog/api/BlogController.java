package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.BlogService;
import com.mainblog.blog.application.BlogViewService;
import com.mainblog.blog.application.BlogViews.BlogDetail;
import com.mainblog.blog.domain.JoinPolicy;
import com.mainblog.blog.domain.Visibility;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 블로그 만들기·보기 (T025, FR-001~FR-006). 목록·검색·수정은 US2·US4에서 더한다. */
@RestController
public class BlogController {

    private final BlogService blogService;
    private final BlogViewService viewService;

    public BlogController(BlogService blogService, BlogViewService viewService) {
        this.blogService = blogService;
        this.viewService = viewService;
    }

    @PostMapping("/api/blogs")
    @ResponseStatus(HttpStatus.CREATED)
    public BlogDetail create(@AuthenticationPrincipal AuthenticatedMember member,
                             @jakarta.validation.Valid @RequestBody BlogCreateRequest req) {
        return blogService.create(member.id(), new BlogService.CreateCommand(req.name(), req.address(),
                req.description(), req.tags(), req.visibility(), req.joinPolicy()));
    }

    @GetMapping("/api/blogs/address/{address}")
    public BlogDetail byAddress(@PathVariable String address) {
        return viewService.byAddress(address);
    }

    public record BlogCreateRequest(@Size(max = 200) String name, @Size(max = 100) String address,
                                    @Size(max = 2000) String description, @Size(max = 50) List<String> tags,
                                    Visibility visibility, JoinPolicy joinPolicy) {}
}
