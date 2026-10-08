package com.mainblog.support;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * 화면 확인용 서버. 평소 테스트에서는 돌지 않는다.
 * {@code ./gradlew test --tests '*UiPreviewServer' -PuiPreview=true}로 띄우면 시드(local/seed.sql)를 넣고
 * build/ui-preview-port 파일에 포트를 적은 뒤 build/ui-preview-stop 파일이 생길 때까지(최대 30분) 기다린다.
 */
@EnabledIfSystemProperty(named = "ui.preview", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UiPreviewServer extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired
    DataSource dataSource;

    @Test
    void 화면_확인용_서버() throws Exception {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new FileSystemResource(Path.of("local/seed.sql")));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
        Path stop = Path.of("build/ui-preview-stop");
        Files.deleteIfExists(stop);
        Files.writeString(Path.of("build/ui-preview-port"), String.valueOf(port));
        long until = System.currentTimeMillis() + 30 * 60 * 1000L;
        while (System.currentTimeMillis() < until && !Files.exists(stop)) {
            Thread.sleep(1000);
        }
    }
}
