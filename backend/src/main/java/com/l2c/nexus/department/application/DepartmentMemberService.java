package com.l2c.nexus.department.application;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import com.l2c.nexus.department.domain.Department;
import com.l2c.nexus.department.persistence.DepartmentMemberRepository;
import com.l2c.nexus.department.persistence.DepartmentMemberRepository.DepartmentRef;
import com.l2c.nexus.department.persistence.DepartmentMemberRepository.MemberRowPage;
import com.l2c.nexus.department.persistence.DepartmentRepository;
import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.membership.application.MembershipView;
import com.l2c.nexus.membership.domain.MembershipStatus;
import com.l2c.nexus.organization.application.AccessPolicy;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.application.OrganizationService;
import com.l2c.nexus.organization.application.Permission;
import com.l2c.nexus.shared.error.ConflictException;
import com.l2c.nexus.shared.error.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is in which department (ADR-0025). Changes take the organization lock shared with member
 * administration, so assigning someone cannot race with removing them, and lock the department row,
 * so assigning cannot race with deactivating it.
 */
@Service
public class DepartmentMemberService {

    private final DepartmentRepository departments;
    private final DepartmentMemberRepository assignments;
    private final MembershipService memberships;
    private final OrganizationService organizations;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final Clock clock;

    public DepartmentMemberService(
            DepartmentRepository departments,
            DepartmentMemberRepository assignments,
            MembershipService memberships,
            OrganizationService organizations,
            AccessPolicy policy,
            AuditService audit,
            Clock clock) {
        this.departments = departments;
        this.assignments = assignments;
        this.memberships = memberships;
        this.organizations = organizations;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    /** Idempotent: assigning someone who is already in the department changes nothing. */
    @Transactional
    public void assign(OrgContext org, UUID departmentId, UUID membershipId) {
        policy.require(org, Permission.DEPARTMENT_MANAGE);
        organizations.lockForMembershipChanges(org.organizationId());
        Department department =
                departments
                        .lockByIdAndOrganizationId(departmentId, org.organizationId())
                        .orElseThrow(NotFoundException::new);
        MembershipView member = activeMember(org, membershipId);
        if (!department.isActive()) {
            throw new ConflictException(
                    "DEPARTMENT_INACTIVE",
                    "This department is inactive and cannot receive new members.");
        }
        if (assignments.add(org.organizationId(), departmentId, member.id(), clock.instant())) {
            record(AuditEventType.DEPARTMENT_MEMBER_ADDED, org, departmentId, member.id());
        }
    }

    /** Idempotent. Allowed for inactive departments, so they can be emptied. */
    @Transactional
    public void unassign(OrgContext org, UUID departmentId, UUID membershipId) {
        policy.require(org, Permission.DEPARTMENT_MANAGE);
        organizations.lockForMembershipChanges(org.organizationId());
        departments
                .findByIdAndOrganizationId(departmentId, org.organizationId())
                .orElseThrow(NotFoundException::new);
        memberships.find(org.organizationId(), membershipId).orElseThrow(NotFoundException::new);
        if (assignments.remove(org.organizationId(), departmentId, membershipId)) {
            record(AuditEventType.DEPARTMENT_MEMBER_REMOVED, org, departmentId, membershipId);
        }
    }

    @Transactional(readOnly = true)
    public MemberRowPage members(OrgContext org, UUID departmentId, int page, int size) {
        policy.require(org, Permission.DEPARTMENT_VIEW);
        departments
                .findByIdAndOrganizationId(departmentId, org.organizationId())
                .orElseThrow(NotFoundException::new);
        // Email addresses are personal data: only roles that manage membership see them
        boolean includeEmail = policy.can(org.role(), Permission.MEMBER_INVITE);
        return assignments.members(org.organizationId(), departmentId, includeEmail, page, size);
    }

    @Transactional(readOnly = true)
    public List<DepartmentRef> departmentsOf(OrgContext org, UUID membershipId) {
        policy.require(org, Permission.DEPARTMENT_VIEW);
        MembershipView member = activeMember(org, membershipId);
        return assignments.departmentsOf(org.organizationId(), member.id());
    }

    /**
     * Called by member administration when a member is removed or leaves, inside its transaction
     * and under the same organization lock, so no assignment of a revoked member survives.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void clearFor(UUID organizationId, UUID membershipId) {
        assignments.removeAllFor(organizationId, membershipId);
    }

    private MembershipView activeMember(OrgContext org, UUID membershipId) {
        return memberships
                .find(org.organizationId(), membershipId)
                .filter(member -> member.status() == MembershipStatus.ACTIVE)
                .orElseThrow(NotFoundException::new);
    }

    private void record(AuditEventType type, OrgContext org, UUID departmentId, UUID membershipId) {
        audit.record(
                AuditEvent.of(type, org.userId(), AuditTargetType.DEPARTMENT, departmentId)
                        .inOrganization(org.organizationId())
                        .withMetadata(Map.of("membershipId", membershipId.toString())));
    }
}
