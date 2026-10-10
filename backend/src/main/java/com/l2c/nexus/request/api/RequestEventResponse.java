package com.l2c.nexus.request.api;

import com.l2c.nexus.request.domain.RequestStatus;
import com.l2c.nexus.request.persistence.RequestEventRepository.EventRow;
import java.time.Instant;
import java.util.UUID;

/** action is a workflow action or ASSIGN / UNASSIGN; targetName is set for the latter two. */
public record RequestEventResponse(
        UUID id,
        String action,
        RequestStatus fromStatus,
        RequestStatus toStatus,
        String comment,
        UUID actorMembershipId,
        String actorName,
        String targetName,
        Instant occurredAt) {

    static RequestEventResponse from(EventRow row) {
        return new RequestEventResponse(
                row.id(),
                row.action(),
                row.fromStatus(),
                row.toStatus(),
                row.comment(),
                row.actorMembershipId(),
                row.actorName(),
                row.targetName(),
                row.occurredAt());
    }
}
