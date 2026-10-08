package com.mainblog.admin.api;

import com.mainblog.admin.application.AdminCommentModerationService;
import com.mainblog.admin.application.AdminCommentModerationService.AdminComment;
import com.mainblog.admin.application.AdminPostModerationService;
import com.mainblog.admin.application.AdminPostModerationService.AdminPostDetail;
import com.mainblog.admin.application.AdminPostModerationService.AdminPostSummary;
import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.auth.security.AuthenticatedMember;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 글·댓글 숨김·삭제·복구 (005 T043, ADM-03, FR-015~FR-019, contracts/admin-api.yaml content). */
@RestController
public class AdminContentController {

    private final AdminPostModerationService posts;
    private final AdminCommentModerationService comments;

    public AdminContentController(AdminPostModerationService posts, AdminCommentModerationService comments) {
        this.posts = posts;
        this.comments = comments;
    }

    @GetMapping("/api/admin/posts")
    public Page<AdminPostSummary> posts(@RequestParam(required = false) Long blogId,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size,
                                        @AuthenticationPrincipal AuthenticatedMember me) {
        return posts.list(me.id(), blogId, q, status, page, size);
    }

    @GetMapping("/api/admin/posts/{postId}")
    public AdminPostDetail post(@PathVariable long postId, @AuthenticationPrincipal AuthenticatedMember me) {
        return posts.detail(me.id(), postId);
    }

    @DeleteMapping("/api/admin/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable long postId, @AuthenticationPrincipal AuthenticatedMember me) {
        posts.delete(me.id(), postId);
    }

    @PutMapping("/api/admin/posts/{postId}/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hidePost(@PathVariable long postId, @AuthenticationPrincipal AuthenticatedMember me) {
        posts.hide(me.id(), postId);
    }

    @DeleteMapping("/api/admin/posts/{postId}/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unhidePost(@PathVariable long postId, @AuthenticationPrincipal AuthenticatedMember me) {
        posts.unhide(me.id(), postId);
    }

    @PostMapping("/api/admin/posts/{postId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restorePost(@PathVariable long postId, @AuthenticationPrincipal AuthenticatedMember me) {
        posts.restore(me.id(), postId);
    }

    @GetMapping("/api/admin/comments")
    public Page<AdminComment> comments(@RequestParam(required = false) Long postId,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size,
                                       @AuthenticationPrincipal AuthenticatedMember me) {
        return comments.list(me.id(), postId, status, page, size);
    }

    @DeleteMapping("/api/admin/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable long commentId, @AuthenticationPrincipal AuthenticatedMember me) {
        comments.delete(me.id(), commentId);
    }

    @PutMapping("/api/admin/comments/{commentId}/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideComment(@PathVariable long commentId, @AuthenticationPrincipal AuthenticatedMember me) {
        comments.hide(me.id(), commentId);
    }

    @DeleteMapping("/api/admin/comments/{commentId}/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unhideComment(@PathVariable long commentId, @AuthenticationPrincipal AuthenticatedMember me) {
        comments.unhide(me.id(), commentId);
    }

    @PostMapping("/api/admin/comments/{commentId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreComment(@PathVariable long commentId, @AuthenticationPrincipal AuthenticatedMember me) {
        comments.restore(me.id(), commentId);
    }
}
