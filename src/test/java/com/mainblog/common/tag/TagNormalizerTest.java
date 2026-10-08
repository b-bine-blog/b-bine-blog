package com.mainblog.common.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mainblog.common.error.InvalidInputException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 태그 규칙 6.4 (T010, D-88, D-96). */
class TagNormalizerTest {

    @Test
    void 샵_공백_대소문자를_정리하고_같은_태그를_합친다() {
        assertThat(TagNormalizer.normalizeAll(List.of("#제주 여행", "Java", "java"), 10, 20))
                .containsExactly("제주_여행", "java");
    }

    @Test
    void 앞뒤_공백과_여러_개의_샵을_뺀다() {
        assertThat(TagNormalizer.normalize("  ##봄  ", 20)).isEqualTo("봄");
        assertThat(TagNormalizer.normalize("a   b", 20)).isEqualTo("a_b");
    }

    @Test
    void 허용하지_않는_문자와_길이는_거부한다() {
        assertThat(TagNormalizer.normalize("c++", 20)).isNull();
        assertThat(TagNormalizer.normalize("#", 20)).isNull();
        assertThat(TagNormalizer.normalize("가".repeat(21), 20)).isNull();
        assertThat(TagNormalizer.normalize("가".repeat(20), 20)).isEqualTo("가".repeat(20));
    }

    @Test
    void 개수를_넘거나_잘못된_태그가_있으면_400() {
        List<String> eleven = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add("t" + i);
        }
        assertThatThrownBy(() -> TagNormalizer.normalizeAll(eleven, 10, 20)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> TagNormalizer.normalizeAll(List.of("ok", "no!"), 10, 20))
                .isInstanceOf(InvalidInputException.class);
        // 합친 뒤 10개면 통과
        List<String> dup = new java.util.ArrayList<>(Collections.nCopies(12, "같음"));
        assertThat(TagNormalizer.normalizeAll(dup, 10, 20)).containsExactly("같음");
    }
}
