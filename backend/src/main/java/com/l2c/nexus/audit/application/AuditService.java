package com.l2c.nexus.audit.application;

import com.l2c.nexus.shared.id.TimeOrderedUuids;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final TimeOrderedUuids ids;

    public AuditService(JdbcClient jdbc, Clock clock, TimeOrderedUuids ids) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ids = ids;
    }

    /**
     * Records an event in the caller's transaction. MANDATORY means there must already be one, so
     * the audit row and the business change are committed or rolled back together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent event) {
        List<Object> params = new ArrayList<>();
        params.add(ids.next());
        params.add(event.organizationId());
        params.add(event.actorUserId());
        params.add(event.type().name());
        params.add(event.targetType() == null ? null : event.targetType().name());
        params.add(event.targetId());

        String metadataSql;
        if (event.metadata().isEmpty()) {
            metadataSql = "CAST('{}' AS jsonb)";
        } else {
            // PostgreSQL builds the JSON from bound parameters: nothing to escape in Java.
            StringJoiner pairs = new StringJoiner(", ", "jsonb_build_object(", ")");
            for (Map.Entry<String, String> entry : event.metadata().entrySet()) {
                pairs.add("CAST(? AS text), CAST(? AS text)");
                params.add(entry.getKey());
                params.add(entry.getValue());
            }
            metadataSql = pairs.toString();
        }
        params.add(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        jdbc.sql(
                        "INSERT INTO audit_logs (id, organization_id, actor_user_id, event_type,"
                                + " target_type, target_id, metadata, occurred_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, "
                                + metadataSql
                                + ", ?)")
                .params(params)
                .update();
    }
}
