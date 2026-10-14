package com.l2c.nexus.platform.application;

import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.shared.error.ValidationFailedException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
 * The platform's own audit view: only events of PLATFORM_* types that belong to no organization.
 * User-level events (such as an email being verified) and tenant events never appear here, so the
 * platform cannot browse what people did (ADR-0006, ADR-0012).
 */
@Service
public class PlatformAuditService {

    public record Entry(
            UUID id,
            String eventType,
            String actorName,
            String targetType,
            String targetName,
            Map<String, String> metadata,
            Instant occurredAt) {}

    public record AuditPage(List<Entry> items, long total) {}

    private record Row(
            UUID id,
            String eventType,
            String actorName,
            String targetType,
            String targetName,
            Instant occurredAt) {}

    private record Pair(UUID id, String key, String value) {}

    private static final List<String> PLATFORM_TYPES =
            Arrays.stream(AuditEventType.values())
                    .filter(AuditEventType::platformScoped)
                    .map(Enum::name)
                    .toList();

    private final JdbcClient jdbc;

    public PlatformAuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public AuditPage list(PlatformAdmin admin, AuditEventType eventType, int page, int size) {
        if (eventType != null && !eventType.platformScoped()) {
            throw new ValidationFailedException("eventType", "is not a platform event");
        }
        List<String> types = eventType == null ? PLATFORM_TYPES : List.of(eventType.name());
        String placeholders = types.stream().map(type -> "?").collect(Collectors.joining(","));
        String where = "a.organization_id IS NULL AND a.event_type IN (" + placeholders + ")";

        long total =
                jdbc.sql("SELECT count(*) FROM audit_logs a WHERE " + where)
                        .params(new ArrayList<Object>(types))
                        .query(Long.class)
                        .single();

        List<Object> pageParams = new ArrayList<>(types);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<Row> rows =
                jdbc.sql(
                                "SELECT a.id, a.event_type, au.display_name AS actor_name,"
                                        + " a.target_type, COALESCE(tu.display_name, o.name) AS target_name,"
                                        + " a.occurred_at FROM audit_logs a"
                                        + " LEFT JOIN users au ON au.id = a.actor_user_id"
                                        + " LEFT JOIN users tu ON a.target_type = 'USER' AND tu.id = a.target_id"
                                        + " LEFT JOIN organizations o ON a.target_type = 'ORGANIZATION'"
                                        + " AND o.id = a.target_id"
                                        + " WHERE "
                                        + where
                                        + " ORDER BY a.occurred_at DESC, a.id DESC LIMIT ? OFFSET ?")
                        .params(pageParams)
                        .query(
                                (rs, rowNum) ->
                                        new Row(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("event_type"),
                                                rs.getString("actor_name"),
                                                rs.getString("target_type"),
                                                rs.getString("target_name"),
                                                rs.getObject("occurred_at", OffsetDateTime.class)
                                                        .toInstant()))
                        .list();

        Map<UUID, Map<String, String>> metadata = metadataOf(rows);
        List<Entry> entries =
                rows.stream()
                        .map(
                                row ->
                                        new Entry(
                                                row.id(),
                                                row.eventType(),
                                                row.actorName(),
                                                row.targetType(),
                                                row.targetName(),
                                                metadata.getOrDefault(row.id(), Map.of()),
                                                row.occurredAt()))
                        .toList();
        return new AuditPage(entries, total);
    }

    /** PostgreSQL expands each jsonb into key/value rows, so no JSON parsing is needed here. */
    private Map<UUID, Map<String, String>> metadataOf(List<Row> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        String placeholders = rows.stream().map(row -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        rows.forEach(row -> params.add(row.id()));
        List<Pair> pairs =
                jdbc.sql(
                                "SELECT a.id, kv.key, kv.value FROM audit_logs a"
                                        + " CROSS JOIN LATERAL jsonb_each_text(a.metadata) AS kv(key, value)"
                                        + " WHERE a.organization_id IS NULL AND a.id IN ("
                                        + placeholders
                                        + ")")
                        .params(params)
                        .query(
                                (rs, rowNum) ->
                                        new Pair(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("key"),
                                                rs.getString("value")))
                        .list();
        Map<UUID, Map<String, String>> byId = new HashMap<>();
        for (Pair pair : pairs) {
            byId.computeIfAbsent(pair.id(), id -> new TreeMap<>()).put(pair.key(), pair.value());
        }
        return byId;
    }
}
