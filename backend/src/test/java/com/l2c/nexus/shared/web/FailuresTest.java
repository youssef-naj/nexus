package com.l2c.nexus.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.shared.security.RequestBodyTooLargeException;
import com.l2c.nexus.support.LogCapture;
import jakarta.servlet.ServletException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

class FailuresTest {

    @Test
    void connectionProblemsAreUnavailableEvenWhenWrappedDeeply() {
        Throwable wrapped =
                new ServletException(
                        "request failed",
                        new CannotCreateTransactionException(
                                "Could not open JPA EntityManager for transaction",
                                new SQLTransientConnectionException("timed out")));

        assertThat(Failures.classify(wrapped)).isEqualTo(Failures.UNAVAILABLE);
        assertThat(Failures.classify(new DataAccessResourceFailureException("db down")))
                .isEqualTo(Failures.UNAVAILABLE);
    }

    @Test
    void sqlStatesDecideWhetherTheDatabaseIsTheProblem() {
        for (String state : new String[] {"08006", "57P01", "53300"}) {
            assertThat(Failures.classify(new SQLException("x", state)))
                    .as(state)
                    .isEqualTo(Failures.UNAVAILABLE);
        }
        for (String state : new String[] {"23505", "42601", "40001"}) {
            assertThat(Failures.classify(new SQLException("x", state)))
                    .as(state)
                    .isEqualTo(Failures.INTERNAL);
        }
    }

    @Test
    void tooLargeBodiesAreRecognizedWhenWrapped() {
        Throwable wrapped = new RuntimeException(new RequestBodyTooLargeException(100));

        assertThat(Failures.classify(wrapped)).isEqualTo(Failures.TOO_LARGE);
    }

    @Test
    void everythingElseIsInternal() {
        assertThat(Failures.classify(new IllegalStateException("x"))).isEqualTo(Failures.INTERNAL);
        assertThat(Failures.classify(new NullPointerException())).isEqualTo(Failures.INTERNAL);
    }

    @Test
    void theProblemBodyShowsAnIncidentReferenceOnlyForServerErrors() {
        String internal = Failures.problemJson(Failures.INTERNAL, "abc12345");
        String tooLarge = Failures.problemJson(Failures.TOO_LARGE, "abc12345");

        assertThat(internal).contains("\"status\":500").contains("\"incidentId\":\"abc12345\"");
        assertThat(tooLarge).contains("\"status\":413").doesNotContain("incidentId");
    }

    @Test
    void reportingLogsOneLineWithTheIncidentAndNeverTheMessage() {
        try (LogCapture logs = new LogCapture()) {
            String internal =
                    Failures.report(
                            Failures.INTERNAL, new IllegalStateException("secret@example.com"));
            String unavailable =
                    Failures.report(
                            Failures.UNAVAILABLE,
                            new SQLTransientConnectionException("secret@example.com"));

            assertThat(internal).matches("[0-9a-f]{8}");
            assertThat(unavailable).matches("[0-9a-f]{8}").isNotEqualTo(internal);
            assertThat(logs.text())
                    .contains("Unhandled failure, incident " + internal)
                    .contains("Service unavailable, incident " + unavailable)
                    .doesNotContain("secret@example.com");
        }
    }
}
