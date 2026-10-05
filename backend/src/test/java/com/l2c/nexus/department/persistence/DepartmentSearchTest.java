package com.l2c.nexus.department.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DepartmentSearchTest {

    @Test
    void escapesPercentAndUnderscoreSoTheyMatchThemselves() {
        assertThat(DepartmentSearch.escapeLike("50%_off")).isEqualTo("50\\%\\_off");
    }

    @Test
    void escapesTheEscapeCharacterItself() {
        assertThat(DepartmentSearch.escapeLike("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void leavesPlainTextUntouched() {
        assertThat(DepartmentSearch.escapeLike("engineering")).isEqualTo("engineering");
    }
}
