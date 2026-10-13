package com.l2c.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(100);

    @Test
    void aDeclaredOversizedBodyIsRefusedWith413AndNeverReachesTheApplication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setContent(new byte[101]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":413");
        assertThat(chain.getRequest()).as("the chain was not invoked").isNull();
    }

    @Test
    void aBodyWithinTheLimitPassesAndCanBeReadInFull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setContent(new byte[100]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Integer> read = new AtomicReference<>();

        filter.doFilter(
                request,
                response,
                (req, res) ->
                        read.set(
                                ((HttpServletRequest) req).getInputStream().readAllBytes().length));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(read.get()).isEqualTo(100);
    }

    @Test
    void aBodyOfUnknownLengthIsCountedAsItIsReadAndFailsPastTheLimit() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/auth/register") {
                    @Override
                    public long getContentLengthLong() {
                        return -1; // like a chunked upload: nothing declared up front
                    }
                };
        request.setContent(new byte[500]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        request,
                                        response,
                                        (req, res) ->
                                                ((HttpServletRequest) req)
                                                        .getInputStream()
                                                        .readAllBytes()))
                .isInstanceOf(RequestBodyTooLargeException.class);
    }
}
