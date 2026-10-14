package com.mainblog.board.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.board.application.BoardViews.PostDetail;
import com.mainblog.board.application.BoardViews.PostPage;
import com.mainblog.board.application.PostListService;
import com.mainblog.board.application.BoardViews.UploadedImages;
import com.mainblog.board.application.PostQueryService;
import com.mainblog.board.application.PostService;
import com.mainblog.board.application.PostService.PostWriteRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 블로그 글 목록, 글 쓰기·상세·수정·삭제, 글 이미지 올리기 (004 T019·T025·T033, contracts/board-api.yaml post·image). */
@RestController
public class PostController {

    private final PostService postService;
    private final PostQueryService queryService;
    private final PostListService listService;

    public PostController(PostService postService, PostQueryService queryService, PostListService listService) {
        this.postService = postService;
        this.queryService = queryService;
        this.listService = listService;
    }

    @GetMapping("/api/blogs/{blogId}/posts")
    public PostPage list(@PathVariable long blogId, @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size,
                         @RequestParam(required = false) Long categoryId,
                         @RequestParam(required = false) String q) {
        return listService.blogPosts(blogId, categoryId, q, page, size);
    }

    @PostMapping("/api/blogs/{blogId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetail create(@PathVariable long blogId, @RequestBody PostWriteRequest request,
                             @AuthenticationPrincipal AuthenticatedMember member) {
        return postService.create(blogId, member.id(), request);
    }

    @GetMapping("/api/posts/{postId}")
    public PostDetail detail(@PathVariable long postId, @AuthenticationPrincipal AuthenticatedMember member,
                             HttpServletRequest request, HttpServletResponse response) {
        return queryService.open(postId, member == null ? null : member.id(), request, response);
    }

    @PutMapping("/api/posts/{postId}")
    public PostDetail update(@PathVariable long postId, @RequestBody PostWriteRequest request,
                             @AuthenticationPrincipal AuthenticatedMember member) {
        return postService.update(postId, member.id(), request);
    }

    @DeleteMapping("/api/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long postId, @AuthenticationPrincipal AuthenticatedMember member) {
        postService.delete(postId, member.id());
    }

    @PostMapping(value = "/api/blogs/{blogId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedImages uploadImages(@PathVariable long blogId, @RequestPart("files") List<MultipartFile> files,
                                       @AuthenticationPrincipal AuthenticatedMember member) {
        return postService.uploadBlogImages(blogId, member.id(), files);
    }
}
