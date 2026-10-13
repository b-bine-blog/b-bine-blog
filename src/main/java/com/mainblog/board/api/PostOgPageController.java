package com.mainblog.board.api;

import com.mainblog.board.application.OgTagRenderer;
import com.mainblog.common.config.BoardProperties;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** 글·공지 상세 화면: og 태그를 채운 정적 HTML과 미리보기 그림 (004 T049·T053, D-66, D-110, research R-12). */
@Controller
public class PostOgPageController {

    private final OgTagRenderer ogTagRenderer;
    private final BoardProperties props;

    public PostOgPageController(OgTagRenderer ogTagRenderer, BoardProperties props) {
        this.ogTagRenderer = ogTagRenderer;
        this.props = props;
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

    /** 링크 미리보기 그림(D-110). 못 보는 글이면 404. */
    @GetMapping("/og/blog/{address:[a-z0-9-]+}/posts/{postId:\\d+}.png")
    public ResponseEntity<byte[]> blogPostCard(@PathVariable String address, @PathVariable long postId,
                                               @RequestParam(required = false) String share) {
        return png(ogTagRenderer.blogPostCard(address, postId, share));
    }

    @GetMapping("/og/notices/{postId:\\d+}.png")
    public ResponseEntity<byte[]> noticeCard(@PathVariable long postId) {
        return png(ogTagRenderer.noticeCard(postId));
    }

    private ResponseEntity<byte[]> png(Optional<byte[]> image) {
        return image.map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(props.og().cardMaxAge())).body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache()).body(body);
    }
}
