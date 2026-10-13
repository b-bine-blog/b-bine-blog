package com.mainblog.board.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.board.application.BoardViews.CommentPage;
import com.mainblog.board.application.BoardViews.CommentView;
import com.mainblog.board.application.BoardViews.LikeResult;
import com.mainblog.board.application.CommentService;
import com.mainblog.board.application.CommentService.CommentEditRequest;
import com.mainblog.board.application.CommentService.CommentWriteRequest;
import com.mainblog.board.application.LikeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 댓글·답글과 좋아요 (004 T037·T038, FR-036~FR-045, contracts/board-api.yaml comment·like). */
@RestController
public class CommentController {

    private final CommentService commentService;
    private final LikeService likeService;

    public CommentController(CommentService commentService, LikeService likeService) {
        this.commentService = commentService;
        this.likeService = likeService;
    }

    @GetMapping("/api/posts/{postId}/comments")
    public CommentPage list(@PathVariable long postId, @RequestParam(required = false) Integer page,
                            @AuthenticationPrincipal AuthenticatedMember member) {
        return commentService.list(postId, member == null ? null : member.id(), page);
    }

    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView create(@PathVariable long postId, @RequestBody CommentWriteRequest request,
                              @AuthenticationPrincipal AuthenticatedMember member) {
        return commentService.create(postId, member.id(), request);
    }

    @PatchMapping("/api/comments/{commentId}")
    public CommentView update(@PathVariable long commentId, @RequestBody CommentEditRequest request,
                              @AuthenticationPrincipal AuthenticatedMember member) {
        return commentService.update(commentId, member.id(), request);
    }

    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long commentId, @AuthenticationPrincipal AuthenticatedMember member) {
        commentService.delete(commentId, member.id());
    }

    @PutMapping("/api/posts/{postId}/like")
    public LikeResult like(@PathVariable long postId, @AuthenticationPrincipal AuthenticatedMember member) {
        return likeService.toggle(postId, member.id());
    }
}
