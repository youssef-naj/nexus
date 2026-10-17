package com.l2c.nexus.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class SafeExceptionFormatterTest {

    private static Exception deep(int depth) {
        return depth == 0 ? new Exception("deep") : deep(depth - 1);
    }

    @Test
    void neverIncludesTheMessage() {
        String text =
                SafeExceptionFormatter.format(
                        new IllegalStateException("secret@example.com is taken"));

        assertThat(text).contains("java.lang.IllegalStateException");
        assertThat(text).doesNotContain("secret@example.com").doesNotContain("taken");
    }

    @Test
    void describesTheWholeCauseChainByClassNameOnly() {
        Throwable chain =
                new RuntimeException(
                        "outer secret",
                        new IllegalArgumentException(
                                "middle secret", new IOException("inner secret")));

        String text = SafeExceptionFormatter.format(chain);

        assertThat(text)
                .contains(
                        "java.lang.RuntimeException <- caused by java.lang.IllegalArgumentException"
                                + " <- caused by java.io.IOException")
                .doesNotContain("secret");
    }

    @Test
    void includesTheSqlStateOfDatabaseErrorsButNotTheirDetail() {
        SQLException error =
                new SQLException(
                        "Detail: Key (lower(email::text))=(ada@example.com) already exists.",
                        "23505");

        String text = SafeExceptionFormatter.format(error);

        assertThat(text).contains("java.sql.SQLException [sqlState=23505]");
        assertThat(text).doesNotContain("ada@example.com").doesNotContain("Key (");
    }

    @Test
    void limitsTheNumberOfStackFrames() {
        String text = SafeExceptionFormatter.format(deep(40));

        assertThat(text.split(" \\| at ", -1).length - 1)
                .isEqualTo(SafeExceptionFormatter.MAX_FRAMES);
    }

    @Test
    void survivesACyclicCauseChain() {
        Exception a = new Exception("a");
        Exception b = new Exception("b", a);
        a.initCause(b);

        String text = SafeExceptionFormatter.format(a);

        assertThat(text).contains("java.lang.Exception <- caused by java.lang.Exception");
    }

    @Test
    void describesAMissingExceptionWithoutFailing() {
        assertThat(SafeExceptionFormatter.format(null)).isEqualTo("no exception");
    }
}
