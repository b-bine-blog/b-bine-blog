package com.mainblog.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** local/seed.sql이 지금 스키마에 그대로 들어가는지 확인한다 (004 T059, 003 T049). */
class SeedSqlTest extends IntegrationTestBase {

    @Autowired
    DataSource dataSource;

    @Test
    void 내_컴퓨터용_시드가_스키마에_들어간다() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new FileSystemResource(Path.of("local/seed.sql")));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE board_type = 'BLOG'", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE board_type = 'NOTICE'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_tags", Integer.class)).isEqualTo(4);
        // 003 소셜·알림 예시
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follows", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_subscriptions", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT tab) FROM notifications", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT retention_days FROM notification_settings WHERE user_id = 102",
                Integer.class)).isEqualTo(7);
    }
}
