package com.l2c.nexus.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditTargetType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventTest {

    private static AuditEvent organizationCreated() {
        return AuditEvent.of(
                AuditEventType.ORGANIZATION_CREATED,
                UUID.randomUUID(),
                AuditTargetType.ORGANIZATION,
                UUID.randomUUID());
    }

    @Test
    void acceptsTheMetadataKeysTheEventTypeAllows() {
        AuditEvent event =
                organizationCreated().withMetadata(Map.of("name", "Acme", "slug", "acme"));

        assertThat(event.metadata()).containsEntry("name", "Acme").containsEntry("slug", "acme");
    }

    @Test
    void rejectsKeysOutsideTheAllowList() {
        assertThatThrownBy(() -> organizationCreated().withMetadata(Map.of("email", "a@b.c")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void rejectsOverlongValues() {
        Map<String, String> metadata = Map.of("name", "x".repeat(501));

        assertThatThrownBy(() -> organizationCreated().withMetadata(metadata))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullValues() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("name", null);

        assertThatThrownBy(() -> organizationCreated().withMetadata(metadata))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
