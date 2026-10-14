package com.l2c.nexus.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.identity.application.RegisterUserCommand;
import com.l2c.nexus.identity.application.RegistrationService;
import com.l2c.nexus.platform.application.PlatformAdminSyncService;
import com.l2c.nexus.platform.application.PlatformAdminSyncService.SyncResult;
import com.l2c.nexus.support.PlatformFixtures;
import com.l2c.nexus.support.PlatformFixtures.Account;
import com.l2c.nexus.support.VerifiedUserFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Own application context (and database): synchronizing revokes every role not in the list. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nexus.test.context=platform-sync")
@Import(TestcontainersConfiguration.class)
class PlatformAdminSyncIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private PlatformAdminSyncService sync;
    @Autowired private PlatformFixtures fixtures;
    @Autowired private VerifiedUserFactory users;
    @Autowired private RegistrationService registration;
    @Autowired private JdbcClient jdbc;

    private UUID idOf(String email) {
        return jdbc.sql("SELECT id FROM users WHERE lower(email) = lower(?)")
                .param(email)
                .query(UUID.class)
                .single();
    }

    private boolean isAdmin(UUID id) {
        return jdbc.sql("SELECT platform_role IS NOT NULL FROM users WHERE id = ?")
                .param(id)
                .query(Boolean.class)
                .single();
    }

    private List<String> auditTypesFor(UUID userId) {
        return jdbc.sql(
                        "SELECT event_type FROM audit_logs WHERE target_id = ? AND event_type LIKE 'PLATFORM_ADMIN%' ORDER BY occurred_at, id")
                .param(userId)
                .query(String.class)
                .list();
    }

    @Test
    void aVerifiedUserInTheListBecomesAnAdministratorAndTheyCanUseTheApi() {
        String email = users.create();
        UUID id = idOf(email);

        SyncResult result = sync.sync(Set.of(email));

        assertThat(result.granted()).isEqualTo(1);
        assertThat(isAdmin(id)).isTrue();
        assertThat(fixtures.login(port, email).get("/api/platform/organizations").statusCode())
                .isEqualTo(200);
    }

    @Test
    void runningTheSynchronizationAgainChangesNothing() {
        String email = users.create();
        sync.sync(Set.of(email));

        SyncResult again = sync.sync(Set.of(email));

        assertThat(again.granted()).isZero();
        assertThat(again.revoked()).isZero();
        assertThat(auditTypesFor(idOf(email))).containsExactly("PLATFORM_ADMIN_GRANTED");
    }

    @Test
    void removingAnAddressRevokesTheRoleImmediately() {
        Account admin = fixtures.signedIn(port, false);
        sync.sync(Set.of(admin.email()));
        assertThat(admin.http().get("/api/platform/organizations").statusCode()).isEqualTo(200);

        SyncResult result = sync.sync(Set.of());

        assertThat(result.revoked()).isGreaterThanOrEqualTo(1);
        assertThat(isAdmin(admin.userId())).isFalse();
        assertThat(admin.http().get("/api/platform/organizations").statusCode()).isEqualTo(404);
        assertThat(auditTypesFor(admin.userId()))
                .containsExactly("PLATFORM_ADMIN_GRANTED", "PLATFORM_ADMIN_REVOKED");
    }

    @Test
    void unknownUnverifiedAndDisabledAddressesAreNotGranted() {
        String unverified = "unverified-" + UUID.randomUUID() + "@example.com";
        registration.register(
                new RegisterUserCommand(unverified, VerifiedUserFactory.PASSWORD, "Nobody"));
        String disabled = users.create();
        jdbc.sql("UPDATE users SET status = 'DISABLED' WHERE id = ?")
                .param(idOf(disabled))
                .update();

        SyncResult result =
                sync.sync(
                        Set.of(
                                "nobody-" + UUID.randomUUID() + "@example.com",
                                unverified,
                                disabled));

        assertThat(result.granted()).isZero();
        assertThat(result.missing()).isEqualTo(3);
        assertThat(isAdmin(idOf(unverified))).isFalse();
        assertThat(isAdmin(idOf(disabled))).isFalse();
    }

    @Test
    void addressMatchingIgnoresCaseAndSurroundingSpaces() {
        String email = users.create();

        SyncResult result = sync.sync(Set.of("  " + email.toUpperCase() + "  "));

        assertThat(result.granted()).isEqualTo(1);
        assertThat(isAdmin(idOf(email))).isTrue();
    }

    @Test
    void grantsAndRevocationsAreAuditedWithoutAnActorOrPersonalData() {
        String email = users.create();
        UUID id = idOf(email);
        sync.sync(Set.of(email));
        sync.sync(Set.of());

        List<String> rows =
                jdbc.sql(
                                "SELECT coalesce(actor_user_id::text, 'none') || '|' || coalesce(organization_id::text, 'none') || '|' || metadata::text"
                                        + " FROM audit_logs WHERE target_id = ? AND event_type LIKE 'PLATFORM_ADMIN%' ORDER BY occurred_at, id")
                        .param(id)
                        .query(String.class)
                        .list();

        assertThat(rows).containsExactly("none|none|{}", "none|none|{}");
    }
}
