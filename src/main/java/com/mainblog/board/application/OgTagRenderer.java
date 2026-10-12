package com.mainblog.board.application;

import com.mainblog.common.config.AppProperties;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.markdown.MarkdownRenderer;
import com.mainblog.common.markdown.MarkdownText;
import com.mainblog.common.storage.FileStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

/**
 * 글 상세 화면의 미리보기(og·twitter 메타 태그) (004 T049, FR-048~FR-050, D-59, D-66, D-100 #5·#6·#14·#15, research R-12).
 * 정적 HTML의 {@code <!--OG-->} 자리에 메타 태그만 넣는다. 본문은 화면 스크립트가 API로 그린다.
 * 못 보는 글(비공개 블로그, 삭제, 없는 글, share 값이 맞지 않는 일부 공개 글)은 사이트 기본 미리보기다.
 */
@Component
public class OgTagRenderer {

    static final String PLACEHOLDER = "<!--OG-->";
    static final String SITE_NAME = "Blbine Blog";
    static final String SITE_DESCRIPTION = "함께 쓰는 블로그";

    private final JdbcTemplate jdbc;
    private final MarkdownRenderer renderer;
    private final FileStorage fileStorage;
    private final AppProperties app;
    private final BoardProperties props;
    private final Map<String, String> templates = new ConcurrentHashMap<>();

    public OgTagRenderer(JdbcTemplate jdbc, MarkdownRenderer renderer, FileStorage fileStorage, AppProperties app,
                         BoardProperties props) {
        this.jdbc = jdbc;
        this.renderer = renderer;
        this.fileStorage = fileStorage;
        this.app = app;
        this.props = props;
    }

    record Meta(String url, String title, String description, String image) {}

    private record Row(long id, Long blogId, String address, String visibility, String title, String content,
                       String nickname, String userStatus, boolean authorLeft, String coverName) {}

    /** 블로그 글 상세 HTML. address가 글의 블로그와 다르면 기본 미리보기. */
    public String blogPostPage(String address, long postId, String share) {
        String path = "/blog/" + address + "/posts/" + postId;
        return fill("static/blog/post.html", meta(postId, address, share, path));
    }

    /** 공지 상세 HTML. */
    public String noticePage(long postId) {
        return fill("static/notices/post.html", meta(postId, null, null, "/notices/" + postId));
    }

    Meta meta(long postId, String address, String share, String path) {
        Meta fallback = new Meta(app.absolute(path), SITE_NAME, SITE_DESCRIPTION, app.absolute(props.og().defaultImage()));
        List<Row> rows = jdbc.query("SELECT p.id, p.blog_id, b.address, b.visibility, p.title, p.content, u.nickname, "
                + "u.status AS user_status, p.author_left, "
                + "(SELECT ci.stored_name FROM images ci WHERE ci.owner_type = 'BLOG_COVER' AND ci.owner_id = b.id "
                + "AND ci.deleted_at IS NULL ORDER BY ci.id DESC LIMIT 1) AS cover_name "
                + "FROM posts p JOIN users u ON u.id = p.author_id LEFT JOIN blogs b ON b.id = p.blog_id "
                + "WHERE p.id = ? AND p.status = 'VISIBLE' AND (p.board_type = 'NOTICE' "
                + "OR (b.status IN ('ACTIVE', 'CLOSING') AND b.hidden = FALSE))",
                (rs, i) -> new Row(rs.getLong("id"), rs.getObject("blog_id", Long.class), rs.getString("address"),
                        rs.getString("visibility"), rs.getString("title"), rs.getString("content"),
                        rs.getString("nickname"), rs.getString("user_status"), rs.getBoolean("author_left"),
                        rs.getString("cover_name")), postId);
        if (rows.isEmpty()) {
            return fallback;
        }
        Row r = rows.get(0);
        String url = path;
        if (r.blogId() != null) {
            if (address == null || !address.equals(r.address())) {
                return fallback;
            }
            switch (r.visibility()) {
                case "PUBLIC" -> { }
                case "LINK" -> {
                    if (!validShare(r.blogId(), share)) {
                        return fallback;
                    }
                    url = path + "?share=" + UriUtils.encodeQueryParam(share, StandardCharsets.UTF_8);
                }
                default -> {
                    return fallback;
                }
            }
        }
        String html = renderer.toSafeHtml(r.content());
        String image = MarkdownText.firstImage(html);
        if (image == null && r.coverName() != null) {
            image = fileStorage.url(r.coverName());
        }
        if (image == null) {
            image = props.og().defaultImage();
        }
        return new Meta(app.absolute(url), r.title() + " - " + authorName(r),
                MarkdownText.summary(html, props.og().summaryLength()), app.absolute(image));
    }

    private static String authorName(Row r) {
        if ("WITHDRAWN".equals(r.userStatus())) {
            return AuthorView.WITHDRAWN_NAME;
        }
        return r.authorLeft() ? AuthorView.LEFT_BLOG_NAME : r.nickname();
    }

    private boolean validShare(long blogId, String share) {
        if (share == null || share.isBlank() || share.length() > 100) {
            return false;
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM blog_share_links WHERE blog_id = ? AND token = ? "
                + "AND active = TRUE", Integer.class, blogId, share);
        return n != null && n > 0;
    }

    private String fill(String template, Meta m) {
        String page = templates.computeIfAbsent(template, OgTagRenderer::load);
        return page.replace(PLACEHOLDER, tags(m));
    }

    static String tags(Meta m) {
        StringBuilder sb = new StringBuilder();
        meta(sb, "property", "og:type", "article");
        meta(sb, "property", "og:site_name", SITE_NAME);
        meta(sb, "property", "og:url", m.url());
        meta(sb, "property", "og:title", m.title());
        meta(sb, "property", "og:description", m.description());
        meta(sb, "property", "og:image", m.image());
        meta(sb, "name", "twitter:card", "summary_large_image");
        meta(sb, "name", "twitter:title", m.title());
        meta(sb, "name", "twitter:description", m.description());
        meta(sb, "name", "twitter:image", m.image());
        sb.append("<title>").append(HtmlUtils.htmlEscape(m.title())).append("</title>");
        return sb.toString();
    }

    private static void meta(StringBuilder sb, String attr, String key, String value) {
        sb.append("<meta ").append(attr).append("=\"").append(key).append("\" content=\"")
                .append(HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8")).append("\">\n");
    }

    private static String load(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
