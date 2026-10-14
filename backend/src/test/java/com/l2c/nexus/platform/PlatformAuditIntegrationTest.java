package com.l2c.nexus.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.PlatformFixtures;
import com.l2c.nexus.support.PlatformFixtures.Account;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PlatformAuditIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private PlatformFixtures fixtures;
    @Autowired private TenantWorldFactory factory;
    @Autowired private JdbcClient jdbc;

    private TenantWorld world;
    private Account admin;

    @BeforeEach
    void setUp() {
        world = factory.create(port);
        admin = fixtures.signedIn(port, true);
    }

    private void suspendAndReactivate(UUID organization) {
        for (String[] step :
                new String[][] {{"suspend", "NON_PAYMENT"}, {"reactivate", "ISSUE_RESOLVED"}}) {
            String list =
                    admin.http()
                            .get(
                                    "/api/platform/organizations?q="
                                            + nameOf(organization).replace(" ", "+"))
                            .body();
            Matcher matcher =
                    Pattern.compile(
                                    "\\{[^{}]*\"id\":\""
                                            + organization
                                            + "\"[^{}]*\"version\":(\\d+)[^{}]*}")
                            .matcher(list);
            assertThat(matcher.find()).as(list).isTrue();
            HttpResponse<String> response =
                    admin.http()
                            .postJson(
                                    "/api/platform/organizations/" + organization + "/" + step[0],
                                    "{\"reason\":\""
                                            + step[1]
                                            + "\",\"version\":"
                                            + matcher.group(1)
                                            + "}",
                                    admin.http().csrfToken());
            assertThat(response.statusCode()).isEqualTo(200);
        }
    }

    private String nameOf(UUID organization) {
        return jdbc.sql("SELECT name FROM organizations WHERE id = ?")
                .param(organization)
                .query(String.class)
                .single();
    }

    private static List<String> eventTypes(String body) {
        Matcher matcher = Pattern.compile("\"eventType\":\"([A-Z_]+)\"").matcher(body);
        List<String> types = new ArrayList<>();
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
        return types;
    }

    @Test
    void onlyPlatformEventsAreListedNeverUserOrTenantEvents() {
        suspendAndReactivate(world.orgA());
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM audit_logs WHERE event_type = 'USER_EMAIL_VERIFIED' AND organization_id IS NULL")
                                .query(Integer.class)
                                .single())
                .as("such events exist but must stay out of the platform view")
                .isPositive();

        HttpResponse<String> response = admin.http().get("/api/platform/audit?size=100");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(eventTypes(response.body()))
                .isNotEmpty()
                .allMatch(type -> type.startsWith("PLATFORM_"));
        assertThat(response.body())
                .doesNotContain("USER_EMAIL_VERIFIED")
                .doesNotContain("ORGANIZATION_CREATED");
    }

    @Test
    void entriesNameTheActorTheTargetAndTheReason() {
        suspendAndReactivate(world.orgA());

        String body =
                admin.http()
                        .get(
                                "/api/platform/audit?eventType=PLATFORM_ORGANIZATION_REACTIVATED&size=100")
                        .body();

        assertThat(body)
                .contains("\"actorName\":\"Verified\"")
                .contains("\"targetType\":\"ORGANIZATION\"")
                .contains("\"targetName\":\"" + nameOf(world.orgA()) + "\"")
                .contains("\"reason\":\"ISSUE_RESOLVED\"");
        assertThat(body).doesNotContain(world.alice().email()).doesNotContain(admin.email());
    }

    @Test
    void theLogCanBeFilteredByEventTypeAndRefusesNonPlatformTypes() {
        suspendAndReactivate(world.orgA());

        HttpResponse<String> suspended =
                admin.http()
                        .get(
                                "/api/platform/audit?eventType=PLATFORM_ORGANIZATION_SUSPENDED&size=100");

        assertThat(eventTypes(suspended.body()))
                .isNotEmpty()
                .containsOnly("PLATFORM_ORGANIZATION_SUSPENDED");
        assertThat(
                        admin.http()
                                .get("/api/platform/audit?eventType=USER_EMAIL_VERIFIED")
                                .statusCode())
                .isEqualTo(400);
        assertThat(admin.http().get("/api/platform/audit?eventType=NOPE").statusCode())
                .isEqualTo(400);
    }

    @Test
    void theLogIsNewestFirstAndPaginated() {
        suspendAndReactivate(world.orgA());
        suspendAndReactivate(world.orgB());

        HttpResponse<String> firstPage = admin.http().get("/api/platform/audit?size=2&page=0");
        HttpResponse<String> secondPage = admin.http().get("/api/platform/audit?size=2&page=1");

        assertThat(eventTypes(firstPage.body())).hasSize(2);
        assertThat(eventTypes(firstPage.body()).get(0))
                .isEqualTo("PLATFORM_ORGANIZATION_REACTIVATED");
        assertThat(firstPage.body()).contains("\"totalPages\":");
        assertThat(secondPage.statusCode()).isEqualTo(200);
        assertThat(admin.http().get("/api/platform/audit?size=100000").body())
                .contains("\"size\":100");
    }

    @Test
    void ordinaryUsersAndAnonymousCallersCannotReadIt() {
        assertThat(world.alice().http().get("/api/platform/audit").statusCode()).isEqualTo(404);
        assertThat(world.anonymous().get("/api/platform/audit").statusCode()).isEqualTo(401);
    }
}
