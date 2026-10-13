package com.mainblog.board.application;

import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.tag.TagNormalizer;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 글 태그 (004 T018, FR-023~FR-028, D-96, research R-07). 규칙은 002 TagNormalizer와 같고 최대 개수만 게시판 설정값이다.
 */
@Component
public class PostTagService {

    private final JdbcTemplate jdbc;
    private final BoardProperties boardProps;
    private final BlogProperties blogProps;

    public PostTagService(JdbcTemplate jdbc, BoardProperties boardProps, BlogProperties blogProps) {
        this.jdbc = jdbc;
        this.boardProps = boardProps;
        this.blogProps = blogProps;
    }

    /** 정리 규칙을 거친 태그 목록. 규칙에 맞지 않거나 10개를 넘으면 400(field = tags). */
    public List<String> normalize(List<String> raws) {
        return TagNormalizer.normalizeAll(raws, boardProps.tag().maxCount(), blogProps.tag().maxLength());
    }

    /** 태그 하나(태그 목록 주소용). 규칙에 맞지 않으면 null. */
    public String normalizeOne(String raw) {
        return TagNormalizer.normalize(raw, blogProps.tag().maxLength());
    }

    /** 글의 태그를 통째로 바꾼다. 같은 이름을 동시에 만들어도 INSERT IGNORE + 유니크로 하나만 생긴다. */
    public void replace(long postId, List<String> tags) {
        jdbc.update("DELETE FROM post_tags WHERE post_id = ?", postId);
        for (String tag : tags) {
            jdbc.update("INSERT IGNORE INTO tags (name) VALUES (?)", tag);
            Long tagId = jdbc.queryForObject("SELECT id FROM tags WHERE name = ?", Long.class, tag);
            jdbc.update("INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)", postId, tagId);
        }
    }
}
