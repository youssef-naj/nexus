package com.l2c.nexus.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Signed-in accounts for platform tests. The role is set by SQL, as a database operator would. */
@Component
public class PlatformFixtures {

    public record Account(UUID userId, String email, HttpTestClient http) {}

    private final VerifiedUserFactory users;
    private final JdbcClient jdbc;

    public PlatformFixtures(VerifiedUserFactory users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    public Account signedIn(int port, boolean platformAdmin) {
        String email = users.create();
        UUID id =
                jdbc.sql("SELECT id FROM users WHERE lower(email) = lower(?)")
                        .param(email)
                        .query(UUID.class)
                        .single();
        if (platformAdmin) {
            setPlatformAdmin(id, true);
        }
        return new Account(id, email, login(port, email));
    }

    public HttpTestClient login(int port, String email) {
        HttpTestClient http = new HttpTestClient(port);
        assertThat(
                        http.postForm(
                                        "/api/auth/login",
                                        Map.of(
                                                "email",
                                                email,
                                                "password",
                                                VerifiedUserFactory.PASSWORD),
                                        http.csrfToken())
                                .statusCode())
                .isEqualTo(204);
        return http;
    }

    public void setPlatformAdmin(UUID userId, boolean admin) {
        jdbc.sql("UPDATE users SET platform_role = ? WHERE id = ?")
                .params(admin ? "PLATFORM_ADMIN" : null, userId)
                .update();
    }
}
