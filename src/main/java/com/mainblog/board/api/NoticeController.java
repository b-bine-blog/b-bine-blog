package com.mainblog.board.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.board.application.BoardViews.PostDetail;
import com.mainblog.board.application.BoardViews.PostPage;
import com.mainblog.board.application.BoardViews.UploadedImages;
import com.mainblog.board.application.PostListService;
import com.mainblog.board.application.PostService;
import com.mainblog.board.application.PostService.PostWriteRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 메인 공지사항 (004 T052, FR-065, D-100 #19, research R-15). 상세·수정·삭제는 /api/posts/{postId}가 맡는다.
 */
@RestController
public class NoticeController {

    private final PostListService listService;
    private final PostService postService;

    public NoticeController(PostListService listService, PostService postService) {
        this.listService = listService;
        this.postService = postService;
    }

    @GetMapping("/api/notices")
    public PostPage list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return listService.notices(page, size);
    }

    @PostMapping("/api/notices")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetail create(@RequestBody PostWriteRequest request,
                             @AuthenticationPrincipal AuthenticatedMember member) {
        return postService.createNotice(member.id(), request);
    }

    @PostMapping(value = "/api/notices/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedImages uploadImages(@RequestPart("files") List<MultipartFile> files,
                                       @AuthenticationPrincipal AuthenticatedMember member) {
        return postService.uploadNoticeImages(member.id(), files);
    }
}
