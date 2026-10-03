package com.l2c.nexus.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditServiceIntegrationTest {

    @Autowired private AuditService audit;
    @Autowired private JdbcClient jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
    }

    private AuditEvent organizationCreated(UUID actor, UUID org, Map<String, String> metadata) {
        return AuditEvent.of(
                        AuditEventType.ORGANIZATION_CREATED,
                        actor,
                        AuditTargetType.ORGANIZATION,
                        org)
                .inOrganization(org)
                .withMetadata(metadata);
    }

    private int countFor(UUID actor) {
        return jdbc.sql("SELECT count(*) FROM audit_logs WHERE actor_user_id = ?")
                .param(actor)
                .query(Integer.class)
                .single();
    }

    private void recordInTransaction(AuditEvent event) {
        transaction.executeWithoutResult(status -> audit.record(event));
    }

    @Test
    void storesEveryFieldOfTheEvent() {
        UUID actor = UUID.randomUUID();
        UUID org = UUID.randomUUID();

        recordInTransaction(
                organizationCreated(actor, org, Map.of("name", "Acme", "slug", "acme")));

        assertThat(countFor(actor)).isEqualTo(1);
        var row =
                jdbc.sql(
                                "SELECT organization_id, event_type, target_type, target_id,"
                                        + " metadata ->> 'name' AS name, metadata ->> 'slug' AS slug,"
                                        + " occurred_at FROM audit_logs WHERE actor_user_id = ?")
                        .param(actor)
                        .query(
                                (rs, n) ->
                                        Map.<String, Object>of(
                                                "org", rs.getObject("organization_id", UUID.class),
                                                "type", rs.getString("event_type"),
                                                "targetType", rs.getString("target_type"),
                                                "target", rs.getObject("target_id", UUID.class),
                                                "name", rs.getString("name"),
                                                "slug", rs.getString("slug"),
                                                "at",
                                                        rs.getObject(
                                                                "occurred_at",
                                                                OffsetDateTime.class)))
                        .single();
        assertThat(row.get("org")).isEqualTo(org);
        assertThat(row.get("type")).isEqualTo("ORGANIZATION_CREATED");
        assertThat(row.get("targetType")).isEqualTo("ORGANIZATION");
        assertThat(row.get("target")).isEqualTo(org);
        assertThat(row.get("name")).isEqualTo("Acme");
        assertThat(row.get("slug")).isEqualTo("acme");
        OffsetDateTime at = (OffsetDateTime) row.get("at");
        assertThat(Duration.between(at.toInstant(), Instant.now()).abs())
                .isLessThan(Duration.ofSeconds(30));
    }

    @Test
    void metadataWithQuotesAndInjectionAttemptsRoundTripsUnchanged() {
        UUID actor = UUID.randomUUID();
        String nasty = "He said \"hi\" \\ back\nslash '); DROP TABLE audit_logs; --";

        recordInTransaction(organizationCreated(actor, UUID.randomUUID(), Map.of("name", nasty)));

        String stored =
                jdbc.sql("SELECT metadata ->> 'name' FROM audit_logs WHERE actor_user_id = ?")
                        .param(actor)
                        .query(String.class)
                        .single();
        assertThat(stored).isEqualTo(nasty);
    }

    @Test
    void rollsBackTogetherWithTheBusinessTransaction() {
        UUID actor = UUID.randomUUID();
        AuditEvent event = organizationCreated(actor, UUID.randomUUID(), Map.of("name", "Acme"));

        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            audit.record(event);
                                            throw new IllegalStateException("business failure");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(countFor(actor)).isZero();
    }

    @Test
    void refusesToRecordOutsideATransaction() {
        AuditEvent event = organizationCreated(UUID.randomUUID(), UUID.randomUUID(), Map.of());

        assertThatThrownBy(() -> audit.record(event))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void rowsCannotBeUpdated() {
        recordInTransaction(organizationCreated(UUID.randomUUID(), UUID.randomUUID(), Map.of()));

        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_logs SET event_type = 'FORGED'").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void rowsCannotBeDeleted() {
        recordInTransaction(organizationCreated(UUID.randomUUID(), UUID.randomUUID(), Map.of()));

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_logs").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void theTableCannotBeTruncated() {
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE audit_logs").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
