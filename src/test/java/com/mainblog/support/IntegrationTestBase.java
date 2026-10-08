package com.mainblog.support;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.mainblog.common.config.ClockConfig;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import jakarta.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
@AutoConfigureMockMvc
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

    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetClockAndData() {
        clock.setTo(BASE_TIME);
        cleanDatabase();
    }

    /** shedlock을 뺀 모든 테이블을 비운다. 외래 키 확인은 같은 연결 안에서만 끈다. */
    protected void cleanDatabase() {
        jdbc.execute((java.sql.Connection con) -> {
            try (java.sql.Statement st = con.createStatement()) {
                List<String> tables = new java.util.ArrayList<>();
                try (java.sql.ResultSet rs = st.executeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_type = 'BASE TABLE' AND table_name <> 'shedlock'")) {
                    while (rs.next()) {
                        tables.add(rs.getString(1));
                    }
                }
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : tables) {
                    st.execute("TRUNCATE TABLE `" + table + "`");
                }
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

    /** 메일이 count통 올 때까지 기다린다(비동기 발송, R-08). */
    protected MimeMessage[] awaitMails(int count) {
        if (!GREEN_MAIL.waitForIncomingEmail(5000, count)) {
            throw new AssertionError("메일 " + count + "통을 받지 못했습니다. 받은 수: "
                    + GREEN_MAIL.getReceivedMessages().length);
        }
        return GREEN_MAIL.getReceivedMessages();
    }

    /** 메일이 오지 않는지 확인하려고 잠시 기다린 뒤 받은 메일을 돌려준다. */
    protected MimeMessage[] mailsAfterQuietPeriod() throws InterruptedException {
        Thread.sleep(700);
        return GREEN_MAIL.getReceivedMessages();
    }

    /** 그 주소로 온 마지막 메일. */
    protected MimeMessage lastMailTo(String to) throws Exception {
        MimeMessage last = null;
        for (MimeMessage m : GREEN_MAIL.getReceivedMessages()) {
            if (Arrays.stream(m.getAllRecipients()).anyMatch(a -> a.toString().equalsIgnoreCase(to))) {
                last = m;
            }
        }
        if (last == null) {
            throw new AssertionError(to + "로 온 메일이 없습니다");
        }
        return last;
    }

    /** 메일 본문의 6자리 인증번호. */
    protected static String codeIn(MimeMessage message) throws Exception {
        String body = String.valueOf(message.getContent());
        Matcher m = SIX_DIGITS.matcher(body);
        if (!m.find()) {
            throw new AssertionError("메일에 인증번호가 없습니다: " + body);
        }
        return m.group(1);
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
