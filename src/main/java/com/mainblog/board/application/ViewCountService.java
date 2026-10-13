package com.mainblog.board.application;

import com.mainblog.board.application.PostStore.PostRow;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.ratelimit.ClientIpResolver;
import com.mainblog.common.ratelimit.RateLimiter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 조회수 (004 T047, FR-066~FR-068, D-77, D-100 #20·#21, research R-10).
 * 같은 글·같은 사람·같은 날은 post_views 유니크로 한 번만 센다. 서버에 상태를 두지 않는다(SCL-01).
 * 작성자 본인, 봇 User-Agent, 1분에 60개 넘게 연 IP는 세지 않는다. 그래도 글은 정상으로 보인다.
 */
@Component
public class ViewCountService {

    public static final String GUEST_COOKIE = "VID";
    private static final Pattern GUEST_VALUE = Pattern.compile("^[0-9a-f]{32}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final BoardProperties props;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver ipResolver;
    private final Clock clock;

    public ViewCountService(JdbcTemplate jdbc, BoardProperties props, RateLimiter rateLimiter,
                            ClientIpResolver ipResolver, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.rateLimiter = rateLimiter;
        this.ipResolver = ipResolver;
        this.clock = clock;
    }

    /** 새로 셌으면 true. 비회원은 VID 쿠키가 없으면 굽는다. */
    public boolean record(PostRow post, Long userId, HttpServletRequest request, HttpServletResponse response) {
        String viewerKey = userId != null ? "U:" + userId : "G:" + guestId(request, response);
        if (userId != null && userId == post.authorId()) {
            return false;
        }
        if (isBot(request.getHeader(HttpHeaders.USER_AGENT))) {
            return false;
        }
        String ip = ipResolver.resolve(request);
        if (!rateLimiter.tryAcquire("post-view:" + ip, props.view().ipLimit(), props.view().ipWindow())) {
            return false;
        }
        // 글 행을 먼저 잠근다. INSERT의 FK 공유 잠금 뒤에 view_count를 올리면 동시 조회끼리 교착이 생긴다.
        jdbc.queryForObject("SELECT id FROM posts WHERE id = ? FOR UPDATE", Long.class, post.id());
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        int inserted = jdbc.update("INSERT IGNORE INTO post_views (post_id, viewer_key, view_date, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?)", post.id(), viewerKey, today, now, now);
        if (inserted == 1) {
            jdbc.update("UPDATE posts SET view_count = view_count + 1 WHERE id = ?", post.id());
            return true;
        }
        return false;
    }

    boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return props.view().botUserAgents().stream()
                .anyMatch(token -> !token.isBlank() && ua.contains(token.strip().toLowerCase(Locale.ROOT)));
    }

    private String guestId(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (GUEST_COOKIE.equals(c.getName()) && c.getValue() != null && GUEST_VALUE.matcher(c.getValue()).matches()) {
                    return c.getValue();
                }
            }
        }
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String value = HexFormat.of().formatHex(bytes);
        ResponseCookie cookie = ResponseCookie.from(GUEST_COOKIE, value)
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/")
                .maxAge(props.view().guestCookieMaxAge()).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return value;
    }
}
