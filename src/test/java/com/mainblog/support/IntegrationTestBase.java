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
 * - MySQL 8.0 컨테이너에 Crowfoot에서 내보낸 db/schema.sql과 shedlock.sql을 넣는다(R-02, R-13).
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
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("db/schema.sql")),
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

    @Autowired
    protected org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private static final java.util.concurrent.atomic.AtomicInteger IP_SEQ = new java.util.concurrent.atomic.AtomicInteger();

    /** 테스트마다 다른 IP를 써서 메모리 요청 제한(R-06)이 다른 테스트에 번지지 않게 한다. */
    protected static String nextIp() {
        int n = IP_SEQ.incrementAndGet();
        return "10." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
    }

    /** 로그인해서 AT·RT 쿠키 값을 돌려준다. */
    protected LoginCookies loginAs(String email, String password, boolean rememberMe) throws Exception {
        String ip = nextIp();
        org.springframework.mock.web.MockHttpServletResponse res = mvc.perform(AuthTestSupport.postJson(
                        "/api/auth/login", AuthTestSupport.loginJson(email, password, rememberMe, null))
                        .with(r -> { r.setRemoteAddr(ip); return r; }))
                .andReturn().getResponse();
        if (res.getStatus() != 200) {
            throw new AssertionError("로그인 실패: " + res.getStatus() + " " + res.getContentAsString());
        }
        return new LoginCookies(AuthTestSupport.cookieValue(res, "AT"), AuthTestSupport.cookieValue(res, "RT"));
    }

    public record LoginCookies(String at, String rt) {
        public jakarta.servlet.http.Cookie[] cookies() {
            return new jakarta.servlet.http.Cookie[] {
                    new jakarta.servlet.http.Cookie("AT", at), new jakarta.servlet.http.Cookie("RT", rt)};
        }
    }

    /** 블로그와 그 블로그장 멤버십을 만든다. */
    protected long createOwnedBlog(long ownerId, String address, String status) {
        jdbc.update("INSERT INTO blogs (name, address, visibility, join_policy, status, close_scheduled_at) "
                        + "VALUES (?, ?, 'PUBLIC', 'FREE', ?, ?)", "블로그 " + address, address, status,
                "CLOSING".equals(status) ? BASE_TIME.plusDays(7).withHour(4) : null);
        Long blogId = jdbc.queryForObject("SELECT id FROM blogs WHERE address = ?", Long.class, address);
        jdbc.update("INSERT INTO blog_members (user_id, blog_id, role, joined_at) VALUES (?, ?, 'OWNER', ?)",
                ownerId, blogId, BASE_TIME);
        return blogId;
    }

    /** 블로그에 회원을 역할과 함께 넣고 멤버 수를 올린다(002 테스트 도우미, T018). */
    protected void addMember(long blogId, long userId, String role) {
        jdbc.update("INSERT INTO blog_members (user_id, blog_id, role, joined_at, manager_since) VALUES (?, ?, ?, ?, ?)",
                userId, blogId, role, clock.now(), "MANAGER".equals(role) ? clock.now() : null);
        jdbc.update("UPDATE blogs SET member_count = member_count + 1 WHERE id = ?", blogId);
    }

    /** 부블로그장 권한 체크박스를 직접 켠다. */
    protected void setManagerPerms(long blogId, long userId, boolean blogInfo, boolean memberManage,
                                   boolean postManage) {
        jdbc.update("UPDATE blog_members SET role = 'MANAGER', manager_since = COALESCE(manager_since, ?), "
                        + "perm_blog_info = ?, perm_member_manage = ?, perm_post_manage = ? WHERE blog_id = ? AND user_id = ?",
                clock.now(), blogInfo, memberManage, postManage, blogId, userId);
    }

    protected void subscribe(long blogId, long userId) {
        jdbc.update("INSERT INTO blog_subscriptions (blog_id, user_id) VALUES (?, ?)", blogId, userId);
    }

    /** 블로그 안 신고(route = BLOG)를 직접 넣는다. 신고 만들기는 003. */
    protected long insertBlogReport(long blogId, long reporterId, String targetType, long targetId) {
        jdbc.update("INSERT INTO reports (blog_id, reporter_id, target_type, target_id, target_snapshot, reason, route, "
                + "status, created_at) VALUES (?, ?, ?, ?, '신고 당시 내용', 'SPAM', 'BLOG', 'PENDING', ?)",
                blogId, reporterId, targetType, targetId, clock.now());
        return jdbc.queryForObject("SELECT MAX(id) FROM reports", Long.class);
    }

    /** 그 회원이 받은 알림 종류, 받은 순. */
    protected List<String> notificationsOf(long userId) {
        return jdbc.queryForList("SELECT notification_type FROM notifications WHERE recipient_id = ? ORDER BY id",
                String.class, userId);
    }

    /** 이름·전화번호를 정해 회원을 만든다(블랙리스트 비교용). */
    protected long createUser(String email, String password, String nickname, String name, String phone) {
        long id = createUser(email, password, nickname);
        jdbc.update("UPDATE users SET name = ?, phone = ? WHERE id = ?", name, phone, id);
        return id;
    }

    /** 가입 절차 없이 ACTIVE 회원을 만든다. 회원 번호를 돌려준다. */
    protected long createUser(String email, String password, String nickname) {
        org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        String hash = passwordEncoder.encode(password);
        jdbc.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (email, password_hash, name, nickname, phone, role, status, created_at) "
                            + "VALUES (?, ?, '홍길동', ?, '01012345678', 'USER', 'ACTIVE', ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, email);
            ps.setString(2, hash);
            ps.setString(3, nickname);
            ps.setObject(4, BASE_TIME);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    @Autowired
    private com.mainblog.common.ratelimit.InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void resetClockAndData() {
        clock.setTo(BASE_TIME);
        cleanDatabase();
        clearRateLimits();
    }

    /**
     * 메모리 요청 제한을 비운다(003). 회원 번호는 테이블을 비울 때 1부터 다시 시작하고 시계도 같은 시각으로 돌아가서,
     * 회원 단위 제한(팔로우·구독·좋아요)이 앞선 테스트의 횟수를 이어받지 않게 한다.
     */
    private void clearRateLimits() {
        try {
            java.lang.reflect.Field f = com.mainblog.common.ratelimit.InMemoryRateLimiter.class.getDeclaredField("buckets");
            f.setAccessible(true);
            ((java.util.Map<?, ?>) f.get(org.springframework.test.util.AopTestUtils.getTargetObject(rateLimiter))).clear();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
