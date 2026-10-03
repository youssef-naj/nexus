package com.l2c.nexus.organization.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.identity.application.EmailVerificationService;
import com.l2c.nexus.identity.application.RecordingAccountEmails;
import com.l2c.nexus.identity.application.RegisterUserCommand;
import com.l2c.nexus.identity.application.RegistrationService;
import com.l2c.nexus.identity.domain.User;
import com.l2c.nexus.identity.persistence.UserRepository;
import com.l2c.nexus.support.HttpTestClient;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrganizationEndpointsIntegrationTest {

    private static final String STRONG = "correct horse battery staple";

    @Value("${local.server.port}")
    private int port;

    @Autowired private RegistrationService registration;
    @Autowired private EmailVerificationService verification;
    @Autowired private RecordingAccountEmails emails;
    @Autowired private UserRepository users;
    @Autowired private JdbcClient jdbc;

    private HttpTestClient signedIn(String email) {
        registration.register(new RegisterUserCommand(email, STRONG, "Member"));
        verification.verify(emails.awaitNext(email).token());
        HttpTestClient http = new HttpTestClient(port);
        HttpResponse<String> login =
                http.postForm(
                        "/api/auth/login",
                        Map.of("email", email, "password", STRONG),
                        http.csrfToken());
        assertThat(login.statusCode()).isEqualTo(204);
        return http;
    }

    private static String uniqueEmail() {
        return "org-" + UUID.randomUUID() + "@example.com";
    }

    private static String uniqueName() {
        return "Acme " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static HttpResponse<String> createOrganization(HttpTestClient http, String name) {
        return http.postJson("/api/orgs", "{\"name\":\"" + name + "\"}", http.csrfToken());
    }

    @Test
    void anonymousCallersAreRejected() {
        HttpTestClient http = new HttpTestClient(port);

        assertThat(http.get("/api/orgs").statusCode()).isEqualTo(401);
        assertThat(createOrganization(http, "Nope").statusCode()).isEqualTo(401);
    }

    @Test
    void creatingAnOrganizationMakesTheCreatorItsOwnerAndListsIt() {
        HttpTestClient http = signedIn(uniqueEmail());
        String name = uniqueName();

        HttpResponse<String> created = createOrganization(http, name);

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains(name).contains("\"role\":\"OWNER\"");
        HttpResponse<String> list = http.get("/api/orgs");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains(name).contains("\"status\":\"ACTIVE\"");
    }

    @Test
    void aUserNeverSeesAnotherUsersOrganizations() {
        HttpTestClient ada = signedIn(uniqueEmail());
        HttpTestClient eve = signedIn(uniqueEmail());
        String adaOrganization = uniqueName();
        createOrganization(ada, adaOrganization);

        HttpResponse<String> eveList = eve.get("/api/orgs");

        assertThat(eveList.statusCode()).isEqualTo(200);
        assertThat(eveList.body()).isEqualTo("[]");
    }

    @Test
    void invalidNamesAreRejectedWithFieldErrors() {
        HttpTestClient http = signedIn(uniqueEmail());

        HttpResponse<String> blank = createOrganization(http, "");
        HttpResponse<String> control = createOrganization(http, "bad\\u0007name");

        assertThat(blank.statusCode()).isEqualTo(400);
        assertThat(blank.body()).contains("\"errors\"", "\"name\"");
        assertThat(control.statusCode()).isEqualTo(400);
        assertThat(control.body()).contains("\"name\"");
    }

    @Test
    void creatingWithoutACsrfTokenIsForbidden() {
        HttpTestClient http = signedIn(uniqueEmail());

        HttpResponse<String> response = http.postJson("/api/orgs", "{\"name\":\"X\"}", null);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void aDisabledAccountLosesAccessImmediatelyAndItsSessionIsDestroyed() {
        String email = uniqueEmail();
        HttpTestClient http = signedIn(email);
        assertThat(http.get("/api/orgs").statusCode()).isEqualTo(200);

        User user = users.findByEmailCaseInsensitive(email).orElseThrow();
        user.disable(Instant.now());
        users.save(user);

        assertThat(http.get("/api/orgs").statusCode()).isEqualTo(401);
        int sessions =
                jdbc.sql("SELECT count(*) FROM spring_session WHERE principal_name = ?")
                        .param(user.getId().toString())
                        .query(Integer.class)
                        .single();
        assertThat(sessions).isZero();
    }
}
