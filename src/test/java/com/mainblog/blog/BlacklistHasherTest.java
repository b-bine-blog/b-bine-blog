package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.blog.application.BlacklistHasher;
import com.mainblog.common.config.BlacklistProperties;
import org.junit.jupiter.api.Test;

/** 블랙리스트 해시 (T012, FR-057, research R-09). */
class BlacklistHasherTest {

    private final BlacklistHasher hasher = new BlacklistHasher(new BlacklistProperties("secret-a"));

    @Test
    void 정규화한_뒤_같은_값이면_같은_해시() {
        assertThat(hasher.email(" A@B.com ")).isEqualTo(hasher.email("a@b.com"));
        assertThat(hasher.phone("010-1234-5678")).isEqualTo(hasher.phone("01012345678"));
        assertThat(hasher.name(" 홍  길동 ")).isEqualTo(hasher.name("홍 길동"));
        assertThat(hasher.email("a@b.com")).hasSize(64).doesNotContain("a@b.com");
    }

    @Test
    void 종류가_다르거나_키가_다르면_해시가_다르다() {
        assertThat(hasher.name("01012345678")).isNotEqualTo(hasher.phone("01012345678"));
        BlacklistHasher other = new BlacklistHasher(new BlacklistProperties("secret-b"));
        assertThat(other.phone("01012345678")).isNotEqualTo(hasher.phone("01012345678"));
    }
}
