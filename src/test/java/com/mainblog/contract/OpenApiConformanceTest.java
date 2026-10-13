package com.mainblog.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.support.IntegrationTestBase;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * 계약과 실제 매핑 대조 (T072). contracts/member-auth-api.yaml의 경로·메서드가 모두 컨트롤러에 있고,
 * 컨트롤러의 /api 경로도 모두 계약에 있어야 한다.
 */
class OpenApiConformanceTest extends IntegrationTestBase {

    static final Path CONTRACT = Path.of("specs/001-member-auth/contracts/member-auth-api.yaml");
    /** 002 블로그 관리 계약 (002 T072). */
    static final Path BLOG_CONTRACT = Path.of("specs/002-blog-management/contracts/blog-api.yaml");
    /** 004 게시판 계약 (004 T055). */
    static final Path BOARD_CONTRACT = Path.of("specs/004-board/contracts/board-api.yaml");
    /** 003 소셜·알림 계약 (003 T046). */
    static final Path SOCIAL_CONTRACT = Path.of("specs/003-social-notification/contracts/social-api.yaml");
    /** 005 관리자 계약 (005 T053). */
    static final Path ADMIN_CONTRACT = Path.of("specs/005-admin-moderation/contracts/admin-api.yaml");
    /** 006 시스템 문의 계약 (ADM-08, D-112). */
    static final Path INQUIRY_CONTRACT = Path.of("specs/006-system-inquiry/contracts/inquiry-api.yaml");
    /** 운영 규칙 안내 (007, D-114). */
    static final Path SITE_RULES_CONTRACT = Path.of("specs/007-site-rules/contracts/site-rules-api.yaml");
    /** 소셜 로그인 (008, D-125). */
    static final Path SOCIAL_LOGIN_CONTRACT = Path.of("specs/008-social-login/contracts/social-login-api.yaml");
    static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");
    /** 화면이 Turnstile 사이트 키를 읽는 설정 조회. 회원 기능 계약 밖이다(T040). */
    static final Set<String> OUTSIDE_CONTRACT = Set.of("GET /api/public-config");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @SuppressWarnings("unchecked")
    private static Set<String> contractOperations() throws Exception {
        return contractOperations(CONTRACT);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> contractOperations(Path contract) throws Exception {
        try (InputStream in = Files.newInputStream(contract)) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) root.get("paths");
            Set<String> ops = new TreeSet<>();
            paths.forEach((path, item) -> item.keySet().stream().filter(HTTP_METHODS::contains)
                    .forEach(m -> ops.add(m.toUpperCase(Locale.ROOT) + " " + path)));
            return ops;
        }
    }

    private Set<String> mappedOperations() {
        Set<String> ops = new TreeSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> {
            Set<String> patterns = info.getPatternValues();
            info.getMethodsCondition().getMethods().forEach(method -> patterns.stream()
                    .filter(p -> p.startsWith("/api/"))
                    .forEach(p -> ops.add(method.name() + " " + p)));
        });
        return ops;
    }

    @Test
    void 계약의_18개_경로와_모든_메서드가_매핑돼_있다() throws Exception {
        Set<String> contract = contractOperations();
        assertThat(contract.stream().map(op -> op.substring(op.indexOf(' ') + 1)).distinct()).hasSize(18);
        assertThat(mappedOperations()).containsAll(contract);
    }

    @Test
    void 블로그_계약의_모든_경로와_메서드가_매핑돼_있다() throws Exception {
        Set<String> contract = contractOperations(BLOG_CONTRACT);
        assertThat(contract).isNotEmpty();
        Set<String> missing = new TreeSet<>(contract);
        missing.removeAll(mappedOperations());
        assertThat(missing).isEmpty();
    }

    @Test
    void 게시판_계약의_16개_경로와_모든_메서드가_매핑돼_있다() throws Exception {
        Set<String> contract = contractOperations(BOARD_CONTRACT);
        assertThat(contract.stream().map(op -> op.substring(op.indexOf(' ') + 1)).distinct()).hasSize(16);
        Set<String> missing = new TreeSet<>(contract);
        missing.removeAll(mappedOperations());
        assertThat(missing).isEmpty();
    }

    @Test
    void 소셜_알림_계약의_15개_경로와_모든_메서드가_매핑돼_있다() throws Exception {
        Set<String> contract = contractOperations(SOCIAL_CONTRACT);
        assertThat(contract.stream().map(op -> op.substring(op.indexOf(' ') + 1)).distinct()).hasSize(15);
        Set<String> missing = new TreeSet<>(contract);
        missing.removeAll(mappedOperations());
        assertThat(missing).isEmpty();
    }

    @Test
    void 관리자_계약의_22개_경로와_모든_메서드가_매핑돼_있다() throws Exception {
        Set<String> contract = contractOperations(ADMIN_CONTRACT);
        assertThat(contract.stream().map(op -> op.substring(op.indexOf(' ') + 1)).distinct()).hasSize(22);
        assertThat(contract).allMatch(op -> op.contains(" /api/admin/"));
        Set<String> missing = new TreeSet<>(contract);
        missing.removeAll(mappedOperations());
        assertThat(missing).isEmpty();
    }

    @Test
    void 시스템_문의_계약의_모든_경로와_메서드가_매핑돼_있다() throws Exception {
        Set<String> contract = contractOperations(INQUIRY_CONTRACT);
        assertThat(contract).hasSize(5);
        Set<String> missing = new TreeSet<>(contract);
        missing.removeAll(mappedOperations());
        assertThat(missing).isEmpty();
    }

    @Test
    void 운영_규칙_계약의_경로가_매핑돼_있다() throws Exception {
        assertThat(mappedOperations()).containsAll(contractOperations(SITE_RULES_CONTRACT));
    }

    @Test
    void 소셜_로그인_계약의_8개_메서드가_매핑돼_있다() throws Exception {
        Set<String> contract = contractOperations(SOCIAL_LOGIN_CONTRACT);
        assertThat(contract).hasSize(8);
        Set<String> missing = new TreeSet<>(contract);
        missing.removeAll(mappedOperations());
        assertThat(missing).isEmpty();
    }

    @Test
    void 계약에_없는_API가_없다() throws Exception {
        Set<String> extra = new TreeSet<>(mappedOperations());
        extra.removeAll(contractOperations());
        extra.removeAll(contractOperations(BLOG_CONTRACT));
        extra.removeAll(contractOperations(BOARD_CONTRACT));
        extra.removeAll(contractOperations(SOCIAL_CONTRACT));
        extra.removeAll(contractOperations(ADMIN_CONTRACT));
        extra.removeAll(contractOperations(INQUIRY_CONTRACT));
        extra.removeAll(contractOperations(SITE_RULES_CONTRACT));
        extra.removeAll(contractOperations(SOCIAL_LOGIN_CONTRACT));
        extra.removeAll(OUTSIDE_CONTRACT);
        assertThat(extra).isEmpty();
    }
}
