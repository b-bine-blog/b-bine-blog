package com.mainblog.admin.api;

import com.mainblog.admin.application.AdminGuard;
import com.mainblog.auth.security.AuthCookies;
import com.mainblog.blog.application.BlogPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 관리자 화면 /admin (005 T009, D-103 #1, research R-03).
 * 화면 HTML은 static 밖(classpath:admin/index.html)에 두고 role을 확인한 뒤에만 내려준다.
 * 비로그인은 로그인 화면으로 보내고(로그인 유지 토큰이 있으면 먼저 토큰을 새로 받아 본다), 관리자가 아니면 404다(*가정*: 페이지가 있는지 드러내지 않음).
 * 화면이 부르는 /api/admin/** API가 요청마다 다시 확인한다.
 */
@Controller
public class AdminPageController {

    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    private static final String NOT_FOUND_PAGE = """
            <!doctype html>
            <html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>찾을 수 없음 - 블바인 블로그</title><link rel="stylesheet" href="/css/base.css"></head>
            <body><main><h1>페이지를 찾을 수 없습니다</h1><p><a href="/">메인으로</a></p></main></body></html>
            """;
    /** 액세스 토큰만 만료됐을 때: 토큰을 새로 받고 다시 연다(api.js 401 처리와 같음). 인라인 스크립트는 CSP로 막혀 파일로 둔다. */
    private static final String REFRESH_PAGE = """
            <!doctype html>
            <html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>관리자 - 블바인 블로그</title><link rel="stylesheet" href="/css/base.css"></head>
            <body data-no-header><main><p>로그인 상태를 확인하고 있습니다…</p></main>
            <script src="/js/api.js"></script><script src="/js/admin-refresh.js"></script></body></html>
            """;

    private final AdminGuard adminGuard;

    public AdminPageController(AdminGuard adminGuard) {
        this.adminGuard = adminGuard;
    }

    @GetMapping({"/admin", "/admin/", "/admin/**"})
    public ResponseEntity<String> page(HttpServletRequest request) throws IOException {
        Long userId = BlogPermissionService.currentUserId();
        if (userId == null) {
            if (AuthCookies.read(request, AuthCookies.REFRESH) != null) {
                return html(HttpStatus.OK, REFRESH_PAGE);
            }
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/login.html?next=/admin")).build();
        }
        if (!adminGuard.isAdmin(userId)) {
            return html(HttpStatus.NOT_FOUND, NOT_FOUND_PAGE);
        }
        try (InputStream in = new ClassPathResource("admin/index.html").getInputStream()) {
            return html(HttpStatus.OK, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(HTML).cacheControl(CacheControl.noStore()).body(body);
    }
}
