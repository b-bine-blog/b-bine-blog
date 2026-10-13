package com.mainblog.board.application;

import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.storage.FileStorage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시판 04:00 정리 단계 (004 T054, FR-013, SC-012, D-40, D-100 #4, research R-11). DailyCleanupJob(ShedLock)이 부른다.
 * 기준 시각 이전 것을 모두 처리하므로 실행하지 못한 날이 있어도 다음 실행에서 밀린 것까지 정리된다.
 */
@Component
public class BoardDailyTasks {

    private static final Logger log = LoggerFactory.getLogger(BoardDailyTasks.class);

    private final JdbcTemplate jdbc;
    private final FileStorage fileStorage;
    private final BoardProperties props;
    private final TransactionTemplate transaction;

    public BoardDailyTasks(JdbcTemplate jdbc, FileStorage fileStorage, BoardProperties props,
                           PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.fileStorage = fileStorage;
        this.props = props;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Result run(LocalDateTime now) {
        int posts = inTx(() -> purgeDeletedPosts(now));
        int comments = inTx(() -> purgeDeletedComments(now));
        int orphans = inTx(() -> markOrphanImages(now));
        int images = inTx(() -> purgeDeletedImages(now));
        int views = inTx(() -> jdbc.update("DELETE FROM post_views WHERE view_date < ?",
                now.minus(props.view().retention()).toLocalDate()));
        return new Result(posts, comments, orphans, images, views);
    }

    /** 1. 삭제 30일 지난 글: 이미지는 삭제 표시, 글 행 삭제(댓글·좋아요·조회·태그는 FK CASCADE). */
    private int purgeDeletedPosts(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(props.purgeAfter());
        List<Long> ids = jdbc.queryForList("SELECT id FROM posts WHERE status = 'DELETED' AND deleted_at <= ?",
                Long.class, cutoff);
        for (Long id : ids) {
            jdbc.update("UPDATE images SET deleted_at = ? WHERE owner_type = 'POST' AND owner_id = ? "
                    + "AND deleted_at IS NULL", now, id);
            // 답글이 부모 댓글을 FK로 가리켜서 CASCADE 순서에 맡기지 않고 답글부터 지운다
            jdbc.update("DELETE FROM comments WHERE post_id = ? AND parent_id IS NOT NULL", id);
            jdbc.update("DELETE FROM posts WHERE id = ?", id);
        }
        return ids.size();
    }

    /** 2. 삭제 30일 지난 댓글. 답글이 남은 최상위 댓글은 답글이 모두 정리될 때까지 남긴다. */
    private int purgeDeletedComments(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(props.purgeAfter());
        int replies = jdbc.update("DELETE FROM comments WHERE status = 'DELETED' AND deleted_at <= ? "
                + "AND parent_id IS NOT NULL", cutoff);
        int tops = jdbc.update("DELETE c FROM comments c WHERE c.status = 'DELETED' AND c.deleted_at <= ? "
                + "AND c.parent_id IS NULL AND NOT EXISTS (SELECT 1 FROM (SELECT parent_id FROM comments "
                + "WHERE parent_id IS NOT NULL) r WHERE r.parent_id = c.id)", cutoff);
        return replies + tops;
    }

    /** 3. 글에 잇지 않은 채 1일 지난 글 이미지는 삭제 표시(D-100 #4). */
    private int markOrphanImages(LocalDateTime now) {
        return jdbc.update("UPDATE images SET deleted_at = ? WHERE owner_type = 'POST' AND owner_id IS NULL "
                + "AND deleted_at IS NULL AND created_at <= ?", now, now.minus(props.image().orphanAfter()));
    }

    /** 4. 삭제 표시 30일 지난 글 이미지는 파일과 행을 지운다. */
    private int purgeDeletedImages(LocalDateTime now) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, stored_name FROM images "
                + "WHERE owner_type = 'POST' AND deleted_at <= ?", now.minus(props.image().purgeAfter()));
        for (Map<String, Object> row : rows) {
            try {
                fileStorage.delete((String) row.get("stored_name"));
            } catch (IOException | IllegalArgumentException e) {
                log.warn("글 이미지 파일 삭제 실패: {}", e.getClass().getSimpleName());
            }
            jdbc.update("DELETE FROM images WHERE id = ?", row.get("id"));
        }
        return rows.size();
    }

    private int inTx(java.util.function.IntSupplier work) {
        Integer n = transaction.execute(status -> work.getAsInt());
        return n == null ? 0 : n;
    }

    public record Result(int purgedPosts, int purgedComments, int orphanImages, int purgedImages, int purgedViews) {

        public static final Result EMPTY = new Result(0, 0, 0, 0, 0);
    }
}
