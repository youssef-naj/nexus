package com.l2c.nexus.request.application;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.membership.application.MembershipView;
import com.l2c.nexus.membership.domain.MembershipStatus;
import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.application.AccessPolicy;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.application.OrganizationService;
import com.l2c.nexus.organization.application.Permission;
import com.l2c.nexus.request.domain.RequestStatus;
import com.l2c.nexus.request.domain.ServiceRequest;
import com.l2c.nexus.request.persistence.RequestDetail;
import com.l2c.nexus.request.persistence.RequestEventRepository;
import com.l2c.nexus.request.persistence.RequestQueries;
import com.l2c.nexus.request.persistence.ServiceRequestRepository;
import com.l2c.nexus.shared.error.ConflictException;
import com.l2c.nexus.shared.error.NotFoundException;
import com.l2c.nexus.shared.error.ValidationFailedException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assigning submitted requests to reviewers (ADR-0031). Assignment is advisory: any reviewer may
 * still decide. Lock order is always organization (shared) then the request row, so an assignment
 * cannot interleave with a role change or removal of the assignee.
 */
@Service
public class RequestAssignmentService {

    public record Reviewer(UUID membershipId, UUID userId, String displayName, OrgRole role) {}

    private static final int MAX_REVIEWERS = 100;

    private final ServiceRequestRepository requests;
    private final RequestEventRepository events;
    private final RequestQueries queries;
    private final MembershipService memberships;
    private final OrganizationService organizations;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final JdbcClient jdbc;
    private final Clock clock;

    public RequestAssignmentService(
            ServiceRequestRepository requests,
            RequestEventRepository events,
            RequestQueries queries,
            MembershipService memberships,
            OrganizationService organizations,
            AccessPolicy policy,
            AuditService audit,
            JdbcClient jdbc,
            Clock clock) {
        this.requests = requests;
        this.events = events;
        this.queries = queries;
        this.memberships = memberships;
        this.organizations = organizations;
        this.policy = policy;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public RequestDetail assign(
            OrgContext context, UUID requestId, UUID assigneeMembershipId, long expectedVersion) {
        return change(context, requestId, assigneeMembershipId, expectedVersion);
    }

    @Transactional
    public RequestDetail unassign(OrgContext context, UUID requestId, long expectedVersion) {
        return change(context, requestId, null, expectedVersion);
    }

    /** A UI hint: whether this viewer may assign this request now. Enforced again on use. */
    public boolean canAssign(OrgContext org, RequestDetail detail) {
        return policy.can(org.role(), Permission.REQUEST_REVIEW)
                && detail.status() == RequestStatus.SUBMITTED;
    }

    /** Active members who can review, for the assignment picker. */
    @Transactional(readOnly = true)
    public List<Reviewer> reviewers(OrgContext org) {
        policy.require(org, Permission.REQUEST_REVIEW);
        List<String> roles =
                Arrays.stream(OrgRole.values())
                        .filter(Permission.REQUEST_REVIEW::isHeldBy)
                        .map(Enum::name)
                        .toList();
        String placeholders = roles.stream().map(role -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        params.add(org.organizationId());
        params.addAll(roles);
        params.add(MAX_REVIEWERS);
        return jdbc.sql(
                        "SELECT m.id, m.user_id, u.display_name, m.role FROM memberships m"
                                + " JOIN users u ON u.id = m.user_id"
                                + " WHERE m.organization_id = ? AND m.status = 'ACTIVE'"
                                + " AND m.role IN ("
                                + placeholders
                                + ") ORDER BY lower(u.display_name), m.id LIMIT ?")
                .params(params)
                .query(
                        (rs, rowNum) ->
                                new Reviewer(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("user_id", UUID.class),
                                        rs.getString("display_name"),
                                        OrgRole.valueOf(rs.getString("role"))))
                .list();
    }

    /**
     * Called by member administration when a member is removed, leaves or can no longer review,
     * inside its transaction and under the exclusive organization lock. Open requests lose the
     * assignment (the version moves, so concurrent editors notice); decided requests keep it as a
     * record of who handled them.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void clearFor(UUID organizationId, UUID membershipId) {
        jdbc.sql(
                        "UPDATE service_requests SET assignee_membership_id = NULL, updated_at = ?,"
                                + " version = version + 1 WHERE organization_id = ?"
                                + " AND assignee_membership_id = ?"
                                + " AND status IN ('SUBMITTED', 'CHANGES_REQUESTED')")
                .params(
                        OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                        organizationId,
                        membershipId)
                .update();
    }

    private RequestDetail change(
            OrgContext context, UUID requestId, UUID assigneeMembershipId, long expectedVersion) {
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
        policy.require(actor, Permission.REQUEST_REVIEW);

        if (request.getStatus() != RequestStatus.SUBMITTED) {
            throw new ConflictException(
                    "REQUEST_NOT_ASSIGNABLE", "Only a submitted request can be assigned.");
        }
        if (request.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "STALE_VERSION",
                    "This request changed since you opened it. Reload it and try again.");
        }
        UUID current = request.getAssigneeMembershipId();
        if (Objects.equals(current, assigneeMembershipId)) {
            return queries.find(actor.organizationId(), requestId, null).orElseThrow();
        }

        Instant now = clock.instant();
        String action;
        UUID target;
        AuditEventType auditType;
        if (assigneeMembershipId == null) {
            request.clearAssignee(now);
            action = "UNASSIGN";
            auditType = AuditEventType.REQUEST_UNASSIGNED;
            target = current;
        } else {
            requireEligible(actor, request, assigneeMembershipId);
            request.assignTo(assigneeMembershipId, now);
            action = "ASSIGN";
            auditType = AuditEventType.REQUEST_ASSIGNED;
            target = assigneeMembershipId;
        }
        requests.saveAndFlush(request);
        events.add(
                actor.organizationId(),
                requestId,
                actor.membershipId(),
                action,
                request.getStatus(),
                request.getStatus(),
                null,
                target,
                now);
        audit.record(
                AuditEvent.of(auditType, actor.userId(), AuditTargetType.REQUEST, requestId)
                        .inOrganization(actor.organizationId())
                        .withMetadata(
                                Map.of(
                                        "reference", request.getReference(),
                                        "assigneeMembershipId", target.toString())));
        return queries.find(actor.organizationId(), requestId, null).orElseThrow();
    }

    /** One generic message for every invalid target, so nothing leaks about other members. */
    private void requireEligible(OrgContext actor, ServiceRequest request, UUID assigneeId) {
        memberships
                .find(actor.organizationId(), assigneeId)
                .filter(candidate -> candidate.status() == MembershipStatus.ACTIVE)
                .filter(candidate -> policy.can(candidate.role(), Permission.REQUEST_REVIEW))
                .filter(candidate -> !candidate.id().equals(request.getCreatedByMembershipId()))
                .orElseThrow(
                        () ->
                                new ValidationFailedException(
                                        "membershipId",
                                        "must be a member who can review this request"));
    }
}
