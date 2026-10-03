package com.l2c.nexus.organization.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    private final SlugGenerator slugs = new SlugGenerator();

    @Test
    void lowercasesAndJoinsWordsWithHyphens() {
        assertThat(slugs.baseFrom("Acme Corp")).isEqualTo("acme-corp");
    }

    @Test
    void stripsAccents() {
        assertThat(slugs.baseFrom("Café Été")).isEqualTo("cafe-ete");
    }

    @Test
    void collapsesSymbolsAndTrimsHyphens() {
        assertThat(slugs.baseFrom("  --A &&& B--  ")).isEqualTo("a-b");
    }

    @Test
    void fallsBackWhenNothingUsableRemains() {
        assertThat(slugs.baseFrom("!!!")).isEqualTo("org");
        assertThat(slugs.baseFrom("شركة")).isEqualTo("org");
    }

    @Test
    void truncatesLongNames() {
        assertThat(slugs.baseFrom("a".repeat(100))).hasSize(SlugGenerator.MAX_BASE_LENGTH);
    }

    @Test
    void neverEndsWithAHyphenAfterTruncation() {
        String name = "a".repeat(39) + " bbb";

        assertThat(slugs.baseFrom(name)).isEqualTo("a".repeat(39));
    }
}
