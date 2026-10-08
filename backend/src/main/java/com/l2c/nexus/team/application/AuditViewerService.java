package com.l2c.nexus.team.application;

import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.organization.application.AccessPolicy;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.application.Permission;
import com.l2c.nexus.shared.error.ValidationFailedException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading the organization's audit log (ADR-0029). Read-only: a join with users for the actor's
 * display name (the documented read-model exception, ADR-0022) and the allow-listed metadata. The
 * query is always scoped by organization, so events without an organization never appear.
 */
@Service
public class AuditViewerService {

    public record Entry(
            UUID id,
            String eventType,
            UUID actorUserId,
            String actorName,
            String targetType,
            UUID targetId,
            Map<String, String> metadata,
            Instant occurredAt) {}

    public record AuditPage(List<Entry> items, long total) {}

    private record Row(
            UUID id,
            String eventType,
            UUID actorUserId,
            String actorName,
            String targetType,
            UUID targetId,
            Instant occurredAt) {}

    private record MetadataPair(UUID id, String key, String value) {}

    private final JdbcClient jdbc;
    private final AccessPolicy policy;

    public AuditViewerService(JdbcClient jdbc, AccessPolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public AuditPage list(
            OrgContext org,
            AuditEventType eventType,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        policy.require(org, Permission.AUDIT_VIEW);

        StringBuilder where = new StringBuilder("a.organization_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(org.organizationId());
        if (eventType != null) {
            where.append(" AND a.event_type = ?");
            params.add(eventType.name());
        }
        if (from != null) {
            where.append(" AND a.occurred_at >= ?");
            params.add(OffsetDateTime.ofInstant(startOfDay(from, 0, "from"), ZoneOffset.UTC));
        }
        if (to != null) {
            where.append(" AND a.occurred_at < ?");
            params.add(OffsetDateTime.ofInstant(startOfDay(to, 1, "to"), ZoneOffset.UTC));
        }

        long total =
                jdbc.sql("SELECT count(*) FROM audit_logs a WHERE " + where)
                        .params(params)
                        .query(Long.class)
                        .single();

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<Row> rows =
                jdbc.sql(
                                "SELECT a.id, a.event_type, a.actor_user_id, u.display_name,"
                                        + " a.target_type, a.target_id, a.occurred_at"
                                        + " FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_user_id"
                                        + " WHERE "
                                        + where
                                        + " ORDER BY a.occurred_at DESC, a.id DESC LIMIT ? OFFSET ?")
                        .params(pageParams)
                        .query(
                                (rs, rowNum) ->
                                        new Row(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("event_type"),
                                                rs.getObject("actor_user_id", UUID.class),
                                                rs.getString("display_name"),
                                                rs.getString("target_type"),
                                                rs.getObject("target_id", UUID.class),
                                                rs.getObject("occurred_at", OffsetDateTime.class)
                                                        .toInstant()))
                        .list();

        Map<UUID, Map<String, String>> metadata = metadataOf(org.organizationId(), rows);
        List<Entry> entries =
                rows.stream()
                        .map(
                                row ->
                                        new Entry(
                                                row.id(),
                                                row.eventType(),
                                                row.actorUserId(),
                                                row.actorName(),
                                                row.targetType(),
                                                row.targetId(),
                                                metadata.getOrDefault(row.id(), Map.of()),
                                                row.occurredAt()))
                        .toList();
        return new AuditPage(entries, total);
    }

    /** PostgreSQL expands each jsonb into key/value rows, so no JSON parsing is needed here. */
    private Map<UUID, Map<String, String>> metadataOf(UUID organizationId, List<Row> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        String placeholders = rows.stream().map(row -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        params.add(organizationId);
        rows.forEach(row -> params.add(row.id()));
        List<MetadataPair> pairs =
                jdbc.sql(
                                "SELECT a.id, kv.key, kv.value FROM audit_logs a"
                                        + " CROSS JOIN LATERAL jsonb_each_text(a.metadata) AS kv(key, value)"
                                        + " WHERE a.organization_id = ? AND a.id IN ("
                                        + placeholders
                                        + ")")
                        .params(params)
                        .query(
                                (rs, rowNum) ->
                                        new MetadataPair(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("key"),
                                                rs.getString("value")))
                        .list();
        Map<UUID, Map<String, String>> byId = new HashMap<>();
        for (MetadataPair pair : pairs) {
            byId.computeIfAbsent(pair.id(), id -> new TreeMap<>()).put(pair.key(), pair.value());
        }
        return byId;
    }

    private static Instant startOfDay(LocalDate date, long plusDays, String field) {
        if (date.getYear() < 1900 || date.getYear() > 2200) {
            throw new ValidationFailedException(field, "is out of range");
        }
        try {
            return date.plusDays(plusDays).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeException e) {
            throw new ValidationFailedException(field, "is out of range");
        }
    }
}
