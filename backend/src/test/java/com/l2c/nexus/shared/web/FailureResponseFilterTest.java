package com.l2c.nexus.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.CannotCreateTransactionException;

class FailureResponseFilterTest {

    private final FailureResponseFilter filter = new FailureResponseFilter();

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/anything");
    }

    @Test
    void passesNormalRequestsThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request(), response, (req, res) -> ((HttpServletResponse) res).setStatus(204));

        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    void aDatabaseOutageBecomesA503WithRetryAfterAndAnIncidentReference() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request(),
                response,
                (req, res) -> {
                    throw new CannotCreateTransactionException(
                            "Could not open JPA EntityManager",
                            new SQLTransientConnectionException("timed out"));
                });

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"status\":503")
                .contains("\"incidentId\":\"");
    }

    @Test
    void anUnexpectedFailureBecomesAGeneric500WithoutAnyDetail() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request(),
                response,
                (req, res) -> {
                    throw new IllegalStateException("secret@example.com");
                });

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString())
                .contains("An unexpected error occurred.")
                .contains("\"incidentId\"")
                .doesNotContain("secret@example.com")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void partialOutputIsDiscardedBeforeTheErrorIsWritten() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request(),
                response,
                (req, res) -> {
                    res.getOutputStream().write("partial output".getBytes());
                    throw new IllegalStateException("boom");
                });

        assertThat(response.getContentAsString())
                .doesNotContain("partial output")
                .contains("\"status\":500");
    }

    @Test
    void aResponseThatIsAlreadyCommittedIsLeftToTheContainer() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        request(),
                                        response,
                                        (req, res) -> {
                                            throw new IllegalStateException("too late");
                                        }))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDisconnectedClientIsNotAnIncident() throws UnsupportedEncodingException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        request(),
                                        response,
                                        (req, res) -> {
                                            throw new IOException("Broken pipe");
                                        }))
                .isInstanceOf(IOException.class);
        assertThat(response.getContentAsString()).isEmpty();
    }
}
