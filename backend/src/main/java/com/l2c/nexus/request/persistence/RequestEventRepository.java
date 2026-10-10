package com.l2c.nexus.request.persistence;

import com.l2c.nexus.request.domain.RequestStatus;
import com.l2c.nexus.shared.id.TimeOrderedUuids;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The history of each request. Append-only in the database; names are joined in. */
@Repository
public class RequestEventRepository {

    /** targetName is set for ASSIGN and UNASSIGN events only. */
    public record EventRow(
            UUID id,
            String action,
            RequestStatus fromStatus,
            RequestStatus toStatus,
            String comment,
            UUID actorMembershipId,
            String actorName,
            String targetName,
            Instant occurredAt) {}

    private static final int MAX_EVENTS = 500;

    private final JdbcClient jdbc;
    private final TimeOrderedUuids ids;

    public RequestEventRepository(JdbcClient jdbc, TimeOrderedUuids ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    public void add(
            UUID organizationId,
            UUID requestId,
            UUID actorMembershipId,
            String action,
            RequestStatus from,
            RequestStatus to,
            String comment,
            UUID targetMembershipId,
            Instant now) {
        jdbc.sql(
                        "INSERT INTO request_events (id, organization_id, request_id,"
                                + " actor_membership_id, action, from_status, to_status, comment,"
                                + " target_membership_id, occurred_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        ids.next(),
                        organizationId,
                        requestId,
                        actorMembershipId,
                        action,
                        from.name(),
                        to.name(),
                        comment,
                        targetMembershipId,
                        OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }

    public List<EventRow> forRequest(UUID organizationId, UUID requestId) {
        return jdbc.sql(
                        "SELECT e.id, e.action, e.from_status, e.to_status, e.comment,"
                                + " e.actor_membership_id, au.display_name AS actor_name,"
                                + " tu.display_name AS target_name, e.occurred_at"
                                + " FROM request_events e"
                                + " JOIN memberships am ON am.organization_id = e.organization_id"
                                + " AND am.id = e.actor_membership_id"
                                + " JOIN users au ON au.id = am.user_id"
                                + " LEFT JOIN memberships tm ON tm.organization_id = e.organization_id"
                                + " AND tm.id = e.target_membership_id"
                                + " LEFT JOIN users tu ON tu.id = tm.user_id"
                                + " WHERE e.organization_id = ? AND e.request_id = ?"
                                + " ORDER BY e.occurred_at, e.id LIMIT "
                                + MAX_EVENTS)
                .params(organizationId, requestId)
                .query(
                        (rs, rowNum) ->
                                new EventRow(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("action"),
                                        RequestStatus.valueOf(rs.getString("from_status")),
                                        RequestStatus.valueOf(rs.getString("to_status")),
                                        rs.getString("comment"),
                                        rs.getObject("actor_membership_id", UUID.class),
                                        rs.getString("actor_name"),
                                        rs.getString("target_name"),
                                        rs.getObject("occurred_at", OffsetDateTime.class)
                                                .toInstant()))
                .list();
    }
}
