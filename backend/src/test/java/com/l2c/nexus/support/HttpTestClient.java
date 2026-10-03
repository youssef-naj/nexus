package com.l2c.nexus.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** A tiny HTTP client with a cookie jar, behaving like a browser talking to the API. */
public class HttpTestClient {

    private final int port;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

    public HttpTestClient(int port) {
        this.port = port;
    }

    public HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    /** Any request makes the server issue the XSRF-TOKEN cookie, exactly as a browser would. */
    public String csrfToken() {
        assertThat(get("/api/system/ping").statusCode()).isEqualTo(200);
        return cookie("XSRF-TOKEN").orElseThrow(() -> new AssertionError("No XSRF-TOKEN cookie"));
    }

    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    public HttpResponse<String> postJson(String path, String json, String csrf) {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json));
        if (csrf != null) {
            request.header("X-XSRF-TOKEN", csrf);
        }
        return send(request.build());
    }

    public HttpResponse<String> postForm(String path, Map<String, String> fields, String csrf) {
        String body =
                fields.entrySet().stream()
                        .map(
                                e ->
                                        URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                                + "="
                                                + URLEncoder.encode(
                                                        e.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrf != null) {
            request.header("X-XSRF-TOKEN", csrf);
        }
        return send(request.build());
    }

    public static String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    public static long retryAfterSeconds(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Retry-After")
                .map(Long::parseLong)
                .orElseThrow(() -> new AssertionError("No Retry-After header"));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Any method with an optional JSON body, for tests that walk over arbitrary routes. */
    public HttpResponse<String> request(String method, String path, String jsonBody, String csrf) {
        HttpRequest.BodyPublisher body =
                jsonBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).method(method, body);
        if (jsonBody != null) {
            request.header("Content-Type", "application/json");
        }
        if (csrf != null) {
            request.header("X-XSRF-TOKEN", csrf);
        }
        return send(request.build());
    }
}
