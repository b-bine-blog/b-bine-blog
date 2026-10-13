package com.mainblog.blog.application;

import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.storage.FileStorage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 폐쇄 30일 뒤 내용 정리 (T049, BLG-09, FR-008, FR-060, FR-081, 4.5, D-35, D-86, research R-13).
 * 글·댓글·사진·카테고리·블랙리스트·문의·공유 링크·블로그 태그를 지우고 주소를 비운다.
 * 블로그 행, 멤버 행, 제재·신고 기록은 남겨 1년 보관 기록이 어느 블로그 것인지 알 수 있게 한다.
 */
@Service
public class BlogPurgeService {

    private static final Logger log = LoggerFactory.getLogger(BlogPurgeService.class);

    private final JdbcTemplate jdbc;
    private final FileStorage fileStorage;
    private final BlogProperties properties;

    public BlogPurgeService(JdbcTemplate jdbc, FileStorage fileStorage, BlogProperties properties) {
        this.jdbc = jdbc;
        this.fileStorage = fileStorage;
        this.properties = properties;
    }

    /** 폐쇄된 지 purge-after가 지났고 주소가 아직 있는 블로그. 04:00 처리가 블로그마다 {@link #purge}를 따로 부른다. */
    @Transactional(readOnly = true)
    public List<Long> dueBlogIds(LocalDateTime now) {
        return jdbc.queryForList("SELECT id FROM blogs WHERE status = 'CLOSED' AND closed_at <= ? "
                + "AND address IS NOT NULL ORDER BY closed_at, id", Long.class,
                now.minus(properties.closing().purgeAfter()));
    }

    /**
     * 지우는 순서는 외래 키 때문에 정해져 있다. 댓글의 부모 FK에는 CASCADE가 없어 대댓글 → 댓글 → 글 순서로 지운다.
     * 좋아요·글 태그·조회 기록은 글 FK의 CASCADE로 함께 지워진다. 파일은 커밋된 뒤에 지운다.
     */
    @Transactional
    public void purge(long blogId) {
        String postsOfBlog = "SELECT id FROM posts WHERE blog_id = ?";
        jdbc.update("DELETE FROM comments WHERE parent_id IS NOT NULL AND post_id IN (" + postsOfBlog + ")", blogId);
        jdbc.update("DELETE FROM comments WHERE post_id IN (" + postsOfBlog + ")", blogId);

        List<Map<String, Object>> images = jdbc.queryForList("""
                SELECT id, stored_name FROM images
                WHERE (owner_type = 'POST' AND owner_id IN (SELECT id FROM posts WHERE blog_id = ?))
                   OR (owner_type IN ('BLOG_COVER', 'BLOG_BACKGROUND') AND owner_id = ?)""", blogId, blogId);
        for (Map<String, Object> image : images) {
            jdbc.update("DELETE FROM images WHERE id = ?", image.get("id"));
        }
        jdbc.update("DELETE FROM posts WHERE blog_id = ?", blogId);
        jdbc.update("DELETE FROM categories WHERE blog_id = ?", blogId);
        jdbc.update("DELETE FROM blacklist_appeals WHERE blacklist_id IN "
                + "(SELECT id FROM blog_blacklists WHERE blog_id = ?)", blogId);
        jdbc.update("DELETE FROM blog_blacklists WHERE blog_id = ?", blogId);
        jdbc.update("DELETE FROM blog_share_links WHERE blog_id = ?", blogId);
        jdbc.update("DELETE FROM blog_tags WHERE blog_id = ?", blogId);
        jdbc.update("UPDATE blogs SET address = NULL WHERE id = ?", blogId);

        List<String> files = images.stream().map(i -> (String) i.get("stored_name")).toList();
        afterCommit(() -> files.forEach(this::deleteQuietly));
    }

    /** 바꾸거나 지운 대표·배경 이미지(D-121)는 purge-after 뒤 파일과 행을 지운다(images 테이블 설명). */
    @Transactional
    public int purgeDeletedCoverImages(LocalDateTime now) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, stored_name FROM images "
                + "WHERE owner_type IN ('BLOG_COVER', 'BLOG_BACKGROUND') AND deleted_at <= ?", now.minus(properties.coverImage().purgeAfter()));
        for (Map<String, Object> row : rows) {
            jdbc.update("DELETE FROM images WHERE id = ?", row.get("id"));
        }
        List<String> files = rows.stream().map(r -> (String) r.get("stored_name")).toList();
        afterCommit(() -> files.forEach(this::deleteQuietly));
        return rows.size();
    }

    private static void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private void deleteQuietly(String storedName) {
        try {
            fileStorage.delete(storedName);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("이미지 파일 삭제 실패: {}", e.getClass().getSimpleName());
        }
    }
}
