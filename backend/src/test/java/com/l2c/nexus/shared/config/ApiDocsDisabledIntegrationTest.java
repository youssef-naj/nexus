package com.l2c.nexus.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.HttpTestClient;
import com.l2c.nexus.support.VerifiedUserFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiDocsDisabledIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private VerifiedUserFactory users;

    @Test
    void apiDocsAndSwaggerDoNotExistEvenForASignedInUser() {
        String email = users.create();
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

        assertThat(http.get("/v3/api-docs").statusCode()).isEqualTo(404);
        assertThat(http.get("/swagger-ui.html").statusCode()).isEqualTo(404);
        assertThat(http.get("/swagger-ui/index.html").statusCode()).isEqualTo(404);
    }
}
