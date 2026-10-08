package com.mainblog.board.application;

import com.mainblog.board.application.BoardViews.PostDetail;
import com.mainblog.board.application.BoardViews.UploadedImages;
import com.mainblog.board.application.PostPolicy.Viewer;
import com.mainblog.board.application.PostStore.PostRow;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.markdown.MarkdownRenderer;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 글 쓰기·수정·삭제와 이미지 올리기 (004 T017·T033·T052, FR-002~FR-015, FR-029~FR-035, FR-065, D-92, D-100 #1·#19).
 * 블로그 글과 메인 공지가 같은 posts 테이블을 쓴다(FR-001).
 */
@Service
public class PostService {

    /** 받는 요청 본문. 공지는 categoryId·tags를 무시한다. */
    public record PostWriteRequest(String title, String content, Long categoryId, List<String> tags) {}

    private record Input(String title, String content, Long categoryId, List<String> tags) {}

    private final PostStore store;
    private final PostPolicy policy;
    private final PostTagService tagService;
    private final PostImageService imageService;
    private final PostQueryService queryService;
    private final MarkdownRenderer renderer;
    private final NotificationWriter notifications;
    private final BoardProperties props;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AdminPostDelete adminDelete;

    private static final java.time.format.DateTimeFormatter KEY_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public PostService(PostStore store, PostPolicy policy, PostTagService tagService, PostImageService imageService,
                       PostQueryService queryService, MarkdownRenderer renderer, NotificationWriter notifications,
                       BoardProperties props, JdbcTemplate jdbc, Clock clock, @Lazy AdminPostDelete adminDelete) {
        this.store = store;
        this.policy = policy;
        this.tagService = tagService;
        this.imageService = imageService;
        this.queryService = queryService;
        this.renderer = renderer;
        this.notifications = notifications;
        this.props = props;
        this.jdbc = jdbc;
        this.clock = clock;
        this.adminDelete = adminDelete;
    }

    /** 메인 관리자의 블로그 글 삭제를 맡는 쪽(005 AdminPostModerationService). 게시판이 관리자 패키지를 직접 알지 않게 둔다. */
    public interface AdminPostDelete {
        void delete(long adminId, long postId);
    }

    /** 블로그 글 쓰기: 그 블로그 멤버(정지 아님)만. 폐쇄 예정 블로그도 된다(US1-14). */
    @Transactional
    public PostDetail create(long blogId, long userId, PostWriteRequest req) {
        policy.requireWriter(blogId, userId);
        Input in = validate(req, blogId);
        long postId = store.insert(blogId, userId, in.categoryId(), PostStore.BLOG, in.title(), in.content(),
                LocalDateTime.now(clock));
        tagService.replace(postId, in.tags());
        imageService.link(postId, userId, renderer.toSafeHtml(in.content()));
        return queryService.detailAfterWrite(postId, userId);
    }

    /** 공지 쓰기: 메인 관리자만. 공지 알림을 켠 회원에게 알린다(FR-065, research R-15). */
    @Transactional
    public PostDetail createNotice(long userId, PostWriteRequest req) {
        policy.requireAdmin(userId);
        Input in = validate(req, null);
        long postId = store.insert(null, userId, null, PostStore.NOTICE, in.title(), in.content(),
                LocalDateTime.now(clock));
        imageService.link(postId, userId, renderer.toSafeHtml(in.content()));
        notifyNotice(postId, userId, in.title());
        return queryService.detailAfterWrite(postId, userId);
    }

    /** 수정: 블로그 글은 작성자 본인이면서 지금 멤버, 공지는 메인 관리자 누구나. */
    @Transactional
    public PostDetail update(long postId, long userId, PostWriteRequest req) {
        PostRow post = store.findForUpdate(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Viewer viewer = policy.requireReadable(post, userId);
        if (!PostPolicy.canEdit(post, viewer)) {
            throw new BusinessException(ErrorCode.POST_FORBIDDEN);
        }
        Input in = validate(req, post.blogId());
        store.update(postId, in.title(), in.content(), in.categoryId(), LocalDateTime.now(clock));
        if (!post.isNotice()) {
            tagService.replace(postId, in.tags());
        }
        imageService.link(postId, post.isNotice() ? userId : post.authorId(), renderer.toSafeHtml(in.content()));
        return queryService.detailAfterWrite(postId, userId);
    }

    /**
     * 삭제: 30일 보관 뒤 04:00에 지운다. 남이 지우면 작성자에게 끌 수 없는 알림(FR-011).
     * 메인 관리자가 블로그 글을 지우면 활동 기록이 남도록 005 AdminPostModerationService가 처리한다(005 T041, FR-017).
     */
    @Transactional
    public void delete(long postId, long userId) {
        PostRow post = store.findForUpdate(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Viewer viewer = policy.viewerForDelete(post, userId);
        if (!PostPolicy.canDelete(post, viewer)) {
            throw new BusinessException(ErrorCode.POST_FORBIDDEN);
        }
        if (viewer.admin() && !post.isNotice()) {
            adminDelete.delete(userId, postId);
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        store.markDeleted(postId, userId, now);
        if (post.authorId() != userId && !post.isNotice()) {
            notifyDeleted(post, userId, now);
        }
    }

    /**
     * 작성자에게 "내 글 삭제됨"(끌 수 없음). 키에 삭제 시각을 넣어 복구한 뒤 다시 지워도 다시 간다(005 R-11).
     * 005 관리자 삭제도 같은 문구를 쓴다.
     */
    public void notifyDeleted(PostRow post, long actorId, LocalDateTime deletedAt) {
        if (!isActive(post.authorId())) {
            return;
        }
        notifications.write(post.authorId(), NotificationType.POST_DELETED, "POST", post.id(), actorId,
                "'" + post.title() + "' 글이 삭제되었습니다.",
                "POST_DELETED:" + post.id() + ":" + deletedAt.format(KEY_TIME));
    }

    /** 블로그 글 이미지 올리기: 글을 쓸 수 있는 멤버만. */
    @Transactional
    public UploadedImages uploadBlogImages(long blogId, long userId, List<MultipartFile> files) {
        policy.requireWriter(blogId, userId);
        return imageService.upload(userId, files);
    }

    /** 공지 이미지 올리기: 메인 관리자만. */
    @Transactional
    public UploadedImages uploadNoticeImages(long userId, List<MultipartFile> files) {
        policy.requireAdmin(userId);
        return imageService.upload(userId, files);
    }

    private Input validate(PostWriteRequest req, Long blogId) {
        if (req == null) {
            throw new InvalidInputException("title", "NotBlank");
        }
        String title = req.title() == null ? "" : req.title().strip();
        if (title.isEmpty()) {
            throw new InvalidInputException("title", "NotBlank");
        }
        if (title.codePointCount(0, title.length()) > props.post().titleMaxLength()) {
            throw new InvalidInputException("title", "Size");
        }
        String content = req.content() == null ? "" : req.content();
        if (content.isBlank()) {
            throw new InvalidInputException("content", "NotBlank");
        }
        if (content.codePointCount(0, content.length()) > props.post().contentMaxLength()) {
            throw new InvalidInputException("content", "Size");
        }
        if (blogId == null) {
            return new Input(title, content, null, List.of());
        }
        Long categoryId = req.categoryId();
        if (categoryId != null) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE id = ? AND blog_id = ?",
                    Integer.class, categoryId, blogId);
            if (n == null || n == 0) {
                throw new InvalidInputException("categoryId", "NotFound");
            }
        }
        return new Input(title, content, categoryId, tagService.normalize(req.tags()));
    }

    private boolean isActive(long userId) {
        String status = jdbc.query("SELECT status FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null,
                userId);
        return "ACTIVE".equals(status);
    }

    /**
     * 공지 알림을 한 번에 넣는다. 관리자가 아닌 활동 회원 가운데 설정이 켜진 회원(설정 행이 없으면 켜짐).
     * 회원이 많아지면 나눠 넣는다(R-15 *가정*).
     */
    private void notifyNotice(long postId, long adminId, String title) {
        String content = "새 공지사항: " + title;
        jdbc.update("INSERT IGNORE INTO notifications (recipient_id, notification_type, tab, target_type, target_id, "
                        + "actor_id, content, event_key, is_read, created_at) "
                        + "SELECT u.id, ?, ?, 'POST', ?, ?, ?, ?, FALSE, ? FROM users u "
                        + "LEFT JOIN notification_settings ns ON ns.user_id = u.id "
                        + "WHERE u.status = 'ACTIVE' AND u.role = 'USER' AND COALESCE(ns.notice, TRUE) = TRUE",
                NotificationType.NOTICE.name(), NotificationType.NOTICE.tab(), postId, adminId,
                content.length() > 300 ? content.substring(0, 300) : content, "NOTICE:" + postId,
                LocalDateTime.now(clock));
    }
}
