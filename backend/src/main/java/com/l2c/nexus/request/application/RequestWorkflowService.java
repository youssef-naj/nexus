package com.l2c.nexus.request.application;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.membership.application.MembershipView;
import com.l2c.nexus.organization.application.AccessPolicy;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.application.OrganizationService;
import com.l2c.nexus.organization.application.Permission;
import com.l2c.nexus.request.domain.RequestAction;
import com.l2c.nexus.request.domain.RequestStatus;
import com.l2c.nexus.request.domain.ServiceRequest;
import com.l2c.nexus.request.persistence.RequestDetail;
import com.l2c.nexus.request.persistence.RequestEventRepository;
import com.l2c.nexus.request.persistence.RequestEventRepository.EventRow;
import com.l2c.nexus.request.persistence.RequestQueries;
import com.l2c.nexus.request.persistence.ServiceRequestRepository;
import com.l2c.nexus.shared.error.ConflictException;
import com.l2c.nexus.shared.error.ForbiddenActionException;
import com.l2c.nexus.shared.error.NotFoundException;
import com.l2c.nexus.shared.error.ValidationFailedException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submitting and reviewing requests (ADR-0028). Lock order is always: organization (shared), then
 * the request row. The caller's role is re-read under the organization lock, because the gate's
 * snapshot may be stale; the request row lock makes simultaneous decisions queue up.
 */
@Service
public class RequestWorkflowService {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ServiceRequestRepository requests;
    private final RequestEventRepository events;
    private final RequestQueries queries;
    private final MembershipService memberships;
    private final OrganizationService organizations;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final Clock clock;

    public RequestWorkflowService(
            ServiceRequestRepository requests,
            RequestEventRepository events,
            RequestQueries queries,
            MembershipService memberships,
            OrganizationService organizations,
            AccessPolicy policy,
            AuditService audit,
            Clock clock) {
        this.requests = requests;
        this.events = events;
        this.queries = queries;
        this.memberships = memberships;
        this.organizations = organizations;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public RequestDetail transition(
            OrgContext context,
            UUID requestId,
            RequestAction action,
            long expectedVersion,
            String rawComment) {
        String comment = normalizeComment(rawComment, action);

        organizations.lockSharedForActions(context.organizationId());
        MembershipView member =
                memberships
                        .findActiveMembership(context.organizationId(), context.userId())
                        .orElseThrow(NotFoundException::new);
        OrgContext actor =
                new OrgContext(
                        context.organizationId(),
                        context.userId(),
                        member.id(),
                        member.role(),
                        context.status());

        ServiceRequest request =
                requests.lockByIdAndOrganizationId(requestId, actor.organizationId())
                        .orElseThrow(NotFoundException::new);
        boolean creator = request.getCreatedByMembershipId().equals(actor.membershipId());
        if (!creator && !policy.can(actor.role(), Permission.REQUEST_VIEW_ALL)) {
            throw new NotFoundException(); // a request you may not see does not exist for you
        }

        if (action == RequestAction.SUBMIT) {
            policy.require(actor, Permission.REQUEST_CREATE);
            if (!creator) {
                throw new ForbiddenActionException(
                        "NOT_REQUEST_OWNER",
                        "Only the person who created a request can submit it.");
            }
        } else {
            policy.require(actor, Permission.REQUEST_REVIEW);
            if (creator) {
                throw new ForbiddenActionException(
                        "SELF_REVIEW_NOT_ALLOWED", "You cannot review a request you created.");
            }
        }

        RequestStatus from = request.getStatus();
        if (!action.allowedFrom(from)) {
            throw new ConflictException(
                    "INVALID_TRANSITION",
                    "This action is not possible for the request in its current state.");
        }
        if (request.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "STALE_VERSION",
                    "This request changed since you opened it. Reload it and review it again.");
        }

        Instant now = clock.instant();
        request.applyTransition(action, now);
        requests.saveAndFlush(request);
        events.add(
                actor.organizationId(),
                requestId,
                actor.membershipId(),
                action.name(),
                from,
                request.getStatus(),
                comment,
                null,
                now);
        audit.record(
                AuditEvent.of(
                                auditTypeOf(action),
                                actor.userId(),
                                AuditTargetType.REQUEST,
                                requestId)
                        .inOrganization(actor.organizationId())
                        .withMetadata(Map.of("reference", request.getReference())));

        return queries.find(actor.organizationId(), requestId, null).orElseThrow();
    }

    /** The request's history, for anyone who may see the request. */
    @Transactional(readOnly = true)
    public List<EventRow> history(OrgContext org, UUID requestId) {
        UUID onlyMine =
                policy.can(org.role(), Permission.REQUEST_VIEW_ALL) ? null : org.membershipId();
        queries.find(org.organizationId(), requestId, onlyMine).orElseThrow(NotFoundException::new);
        return events.forRequest(org.organizationId(), requestId);
    }

    /** What this viewer may do with the request right now: a UI hint, enforced again on use. */
    public List<RequestAction> availableActions(OrgContext org, RequestDetail detail) {
        boolean creator = detail.createdByMembershipId().equals(org.membershipId());
        List<RequestAction> actions = new ArrayList<>();
        if (creator) {
            if (policy.can(org.role(), Permission.REQUEST_CREATE)
                    && RequestAction.SUBMIT.allowedFrom(detail.status())) {
                actions.add(RequestAction.SUBMIT);
            }
        } else if (policy.can(org.role(), Permission.REQUEST_REVIEW)) {
            for (RequestAction action :
                    List.of(
                            RequestAction.APPROVE,
                            RequestAction.REJECT,
                            RequestAction.REQUEST_CHANGES)) {
                if (action.allowedFrom(detail.status())) {
                    actions.add(action);
                }
            }
        }
        return List.copyOf(actions);
    }

    private static AuditEventType auditTypeOf(RequestAction action) {
        return switch (action) {
            case SUBMIT -> AuditEventType.REQUEST_SUBMITTED;
            case APPROVE -> AuditEventType.REQUEST_APPROVED;
            case REJECT -> AuditEventType.REQUEST_REJECTED;
            case REQUEST_CHANGES -> AuditEventType.REQUEST_CHANGES_REQUESTED;
        };
    }

    private static String normalizeComment(String raw, RequestAction action) {
        String comment = raw == null ? null : raw.trim();
        if (comment != null && comment.isEmpty()) {
            comment = null;
        }
        if (comment == null) {
            if (action.commentRequired()) {
                throw new ValidationFailedException("comment", "is required for this action");
            }
            return null;
        }
        if (comment.length() > MAX_COMMENT_LENGTH) {
            throw new ValidationFailedException("comment", "must be at most 1000 characters");
        }
        for (char character : comment.toCharArray()) {
            boolean allowedWhitespace = character == '\n' || character == '\r' || character == '\t';
            if ((character < 32 && !allowedWhitespace) || character == 127) {
                throw new ValidationFailedException(
                        "comment", "must not contain control characters");
            }
        }
        return comment;
    }
}
