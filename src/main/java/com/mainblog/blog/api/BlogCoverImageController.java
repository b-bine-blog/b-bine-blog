package com.mainblog.blog.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.blog.application.BlogCoverImageService;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 블로그 대표 이미지 (T026, FR-007). 정보 수정 권한(블로그장, 정보 수정 부블로그장). */
@RestController
public class BlogCoverImageController {

    private final BlogCoverImageService service;

    public BlogCoverImageController(BlogCoverImageService service) {
        this.service = service;
    }

    @PutMapping("/api/blogs/{blogId}/cover-image")
    @PreAuthorize("@blogAuth.has(#blogId, 'BLOG_INFO')")
    public CoverImageResponse change(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member,
                                     @RequestParam("file") MultipartFile file) throws IOException {
        return new CoverImageResponse(service.change(blogId, member.id(), file.getBytes(),
                file.getOriginalFilename(), file.getContentType()));
    }

    @DeleteMapping("/api/blogs/{blogId}/cover-image")
    @PreAuthorize("@blogAuth.has(#blogId, 'BLOG_INFO')")
    public ResponseEntity<Void> delete(@PathVariable Long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        service.delete(blogId, member.id());
        return ResponseEntity.noContent().build();
    }

    public record CoverImageResponse(String coverImageUrl) {}
}
