package com.mainblog.support;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.mainblog.common.config.ClockConfig;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * DB·메일이 걸리는 테스트의 기반 (T022, R-14).
 * - MySQL 8.0 컨테이너에 Crowfoot에서 내보낸 docs/04-design/schema.sql과 shedlock.sql을 넣는다(R-02, R-13).
 *   컨테이너는 모든 테스트 클래스가 함께 쓴다.
 * - 메일은 GreenMail(SMTP 3025)로 받는다.
 * - 시계는 MutableClock으로 고정해 각 테스트 시작 때 같은 시각으로 되돌린다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.TestClockConfig.class)
public abstract class IntegrationTestBase {

    /** 테스트 기준 시각(한국 시간). */
    public static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 10, 8, 9, 0, 0);

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mirror.gcr.io/library/mysql:8.0").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("mainblog")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docs/04-design/schema.sql")),
                    "/docker-entrypoint-initdb.d/1-schema.sql")
            .withCopyFileToContainer(MountableFile.forClasspathResource("shedlock.sql"),
                    "/docker-entrypoint-initdb.d/2-shedlock.sql");

    static {
        MYSQL.start();
    }

    @RegisterExtension
    protected static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
            .withPerMethodLifecycle(true);

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.setTo(BASE_TIME);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(BASE_TIME.atZone(ClockConfig.ZONE).toInstant(), ClockConfig.ZONE);
        }
    }

    /** {@link Clock} 대신 MutableClock이 주입되는지 확인할 때 쓴다. */
    protected Clock currentClock() {
        return clock;
    }
}
