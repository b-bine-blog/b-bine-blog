package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.BlogQueryService;
import com.mainblog.blog.application.BlogService;
import com.mainblog.blog.application.BlogViews.BlogPage;
import com.mainblog.blog.application.BlogViews.MyBlogs;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 블로그 만들기·보기·목록·검색·내 블로그 (T025, T032, FR-001~FR-006, FR-014~FR-016). */
@RestController
public class BlogController {

    private final BlogService blogService;
    private final BlogViewService viewService;
    private final BlogQueryService queryService;

    public BlogController(BlogService blogService, BlogViewService viewService, BlogQueryService queryService) {
        this.blogService = blogService;
        this.viewService = viewService;
        this.queryService = queryService;
    }

    @PostMapping("/api/blogs")
    @ResponseStatus(HttpStatus.CREATED)
    public BlogDetail create(@AuthenticationPrincipal AuthenticatedMember member,
                             @jakarta.validation.Valid @RequestBody BlogCreateRequest req) {
        return blogService.create(member.id(), new BlogService.CreateCommand(req.name(), req.address(),
                req.description(), req.tags(), req.visibility(), req.joinPolicy()));
    }

    @GetMapping("/api/blogs")
    public BlogPage list(@RequestParam(required = false) String sort, @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        return queryService.list(sort, page, size);
    }

    @GetMapping("/api/blogs/search")
    public BlogPage search(@RequestParam(required = false) String q, @RequestParam(required = false) String sort,
                           @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queryService.search(q, sort, page, size);
    }

    @GetMapping("/api/me/blogs")
    public MyBlogs myBlogs(@AuthenticationPrincipal AuthenticatedMember member) {
        return queryService.myBlogs(member.id());
    }

    @GetMapping("/api/blogs/address/{address}")
    public BlogDetail byAddress(@PathVariable String address) {
        return viewService.byAddress(address);
    }

    public record BlogCreateRequest(@Size(max = 200) String name, @Size(max = 100) String address,
                                    @Size(max = 2000) String description, @Size(max = 50) List<String> tags,
                                    Visibility visibility, JoinPolicy joinPolicy) {}
}
