package com.mainblog.blog.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** 블로그 태그(tags, blog_tags) 읽기·쓰기 (T006, FR-001, FR-009, D-88). 값은 TagNormalizer를 거친 뒤 넘긴다. */
@Component
public class BlogTagStore {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public BlogTagStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /** 그 블로그의 태그를 통째로 바꾼다. 같은 태그 이름을 동시에 만들어도 INSERT IGNORE + 유니크로 하나만 생긴다. */
    public void replace(long blogId, List<String> tags) {
        jdbc.update("DELETE FROM blog_tags WHERE blog_id = ?", blogId);
        for (String tag : tags) {
            jdbc.update("INSERT IGNORE INTO tags (name) VALUES (?)", tag);
            Long tagId = jdbc.queryForObject("SELECT id FROM tags WHERE name = ?", Long.class, tag);
            jdbc.update("INSERT INTO blog_tags (blog_id, tag_id) VALUES (?, ?)", blogId, tagId);
        }
    }

    public List<String> tagsOf(long blogId) {
        return jdbc.queryForList("SELECT t.name FROM blog_tags bt JOIN tags t ON t.id = bt.tag_id "
                + "WHERE bt.blog_id = ? ORDER BY bt.id", String.class, blogId);
    }

    public Map<Long, List<String>> tagsOf(Collection<Long> blogIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (blogIds.isEmpty()) {
            return result;
        }
        named.query("SELECT bt.blog_id, t.name FROM blog_tags bt JOIN tags t ON t.id = bt.tag_id "
                        + "WHERE bt.blog_id IN (:ids) ORDER BY bt.id",
                new MapSqlParameterSource("ids", blogIds),
                rs -> {
                    result.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
                });
        return result;
    }
}
