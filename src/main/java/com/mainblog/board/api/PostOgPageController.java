package com.mainblog.board.api;

import com.mainblog.board.application.OgTagRenderer;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** 글·공지 상세 화면: og 태그를 채운 정적 HTML (004 T049·T053, D-66, research R-12). */
@Controller
public class PostOgPageController {

    private final OgTagRenderer ogTagRenderer;

    public PostOgPageController(OgTagRenderer ogTagRenderer) {
        this.ogTagRenderer = ogTagRenderer;
    }

    @GetMapping("/blog/{address:[a-z0-9-]+}/posts/{postId:\\d+}")
    public ResponseEntity<String> blogPost(@PathVariable String address, @PathVariable long postId,
                                           @RequestParam(required = false) String share) {
        return html(ogTagRenderer.blogPostPage(address, postId, share));
    }

    @GetMapping("/notices/{postId:\\d+}")
    public ResponseEntity<String> notice(@PathVariable long postId) {
        return html(ogTagRenderer.noticePage(postId));
    }

    private static ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache()).body(body);
    }
}
