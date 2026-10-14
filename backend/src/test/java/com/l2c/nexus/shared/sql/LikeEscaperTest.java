package com.l2c.nexus.shared.sql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikeEscaperTest {

    @Test
    void escapesPercentAndUnderscoreSoTheyMatchThemselves() {
        assertThat(LikeEscaper.escape("50%_off")).isEqualTo("50\\%\\_off");
    }

    @Test
    void escapesTheEscapeCharacterItself() {
        assertThat(LikeEscaper.escape("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void leavesPlainTextUntouched() {
        assertThat(LikeEscaper.escape("engineering")).isEqualTo("engineering");
    }
}
