package com.l2c.nexus.request.application;

import com.l2c.nexus.request.domain.RequestStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only figures about requests, for the dashboard (ADR-0030). The request module owns its
 * tables, so other modules ask here instead of querying them. Every statement is scoped by
 * organization; "only created by" narrows to one member's own requests.
 */
@Service
public class RequestStatistics {

    /** action is a workflow action or ASSIGN / UNASSIGN; targetName is set for the latter two. */
    public record RecentEvent(
            UUID id,
            UUID requestId,
            String reference,
            String title,
            String action,
            RequestStatus toStatus,
            String actorName,
            String targetName,
            Instant occurredAt) {}

    private record StatusCount(RequestStatus status, long count) {}

    private final JdbcClient jdbc;

    public RequestStatistics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One entry for every status, zero when there are no requests in it. */
    @Transactional(readOnly = true)
    public Map<RequestStatus, Long> countByStatus(
            UUID organizationId, UUID onlyCreatedByMembershipId) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT status, count(*) AS n FROM service_requests WHERE organization_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(organizationId);
        if (onlyCreatedByMembershipId != null) {
            sql.append(" AND created_by_membership_id = ?");
            params.add(onlyCreatedByMembershipId);
        }
        sql.append(" GROUP BY status");
        List<StatusCount> rows =
                jdbc.sql(sql.toString())
                        .params(params)
                        .query(
                                (rs, rowNum) ->
                                        new StatusCount(
                                                RequestStatus.valueOf(rs.getString("status")),
                                                rs.getLong("n")))
                        .list();
        Map<RequestStatus, Long> counts = new EnumMap<>(RequestStatus.class);
        for (RequestStatus status : RequestStatus.values()) {
            counts.put(status, 0L);
        }
        for (StatusCount row : rows) {
            counts.put(row.status(), row.count());
        }
        return counts;
    }

    /** Submitted requests created by someone else: what the given member could decide on. */
    @Transactional(readOnly = true)
    public long countAwaitingReview(UUID organizationId, UUID excludeCreatorMembershipId) {
        return jdbc.sql(
                        "SELECT count(*) FROM service_requests WHERE organization_id = ?"
                                + " AND status = 'SUBMITTED' AND created_by_membership_id <> ?")
                .params(organizationId, excludeCreatorMembershipId)
                .query(Long.class)
                .single();
    }

    /** Submitted requests assigned to the given member. */
    @Transactional(readOnly = true)
    public long countAssignedOpenTo(UUID organizationId, UUID assigneeMembershipId) {
        return jdbc.sql(
                        "SELECT count(*) FROM service_requests WHERE organization_id = ?"
                                + " AND assignee_membership_id = ? AND status = 'SUBMITTED'")
                .params(organizationId, assigneeMembershipId)
                .query(Long.class)
                .single();
    }

    /** The newest history events, optionally only on requests created by one member. */
    @Transactional(readOnly = true)
    public List<RecentEvent> recentEvents(
            UUID organizationId, UUID onlyCreatedByMembershipId, int limit) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT e.id, e.request_id, r.reference, r.title, e.action, e.to_status,"
                                + " au.display_name AS actor_name, tu.display_name AS target_name,"
                                + " e.occurred_at FROM request_events e"
                                + " JOIN service_requests r ON r.organization_id = e.organization_id"
                                + " AND r.id = e.request_id"
                                + " JOIN memberships am ON am.organization_id = e.organization_id"
                                + " AND am.id = e.actor_membership_id"
                                + " JOIN users au ON au.id = am.user_id"
                                + " LEFT JOIN memberships tm ON tm.organization_id = e.organization_id"
                                + " AND tm.id = e.target_membership_id"
                                + " LEFT JOIN users tu ON tu.id = tm.user_id"
                                + " WHERE e.organization_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(organizationId);
        if (onlyCreatedByMembershipId != null) {
            sql.append(" AND r.created_by_membership_id = ?");
            params.add(onlyCreatedByMembershipId);
        }
        sql.append(" ORDER BY e.occurred_at DESC, e.id DESC LIMIT ?");
        params.add(limit);
        return jdbc.sql(sql.toString())
                .params(params)
                .query(
                        (rs, rowNum) ->
                                new RecentEvent(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("request_id", UUID.class),
                                        rs.getString("reference"),
                                        rs.getString("title"),
                                        rs.getString("action"),
                                        RequestStatus.valueOf(rs.getString("to_status")),
                                        rs.getString("actor_name"),
                                        rs.getString("target_name"),
                                        rs.getObject("occurred_at", OffsetDateTime.class)
                                                .toInstant()))
                .list();
    }
}
