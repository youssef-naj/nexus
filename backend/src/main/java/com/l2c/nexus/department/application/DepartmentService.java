package com.l2c.nexus.department.application;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import com.l2c.nexus.department.domain.Department;
import com.l2c.nexus.department.domain.DepartmentSort;
import com.l2c.nexus.department.persistence.DepartmentRepository;
import com.l2c.nexus.department.persistence.DepartmentSearch;
import com.l2c.nexus.organization.application.AccessPolicy;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.application.Permission;
import com.l2c.nexus.shared.error.ConflictException;
import com.l2c.nexus.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private static final String NAME_CONSTRAINT = "uq_departments_org_name";
    private static final UUID NO_ONE = new UUID(0, 0);
    private static final int MAX_QUERY_LENGTH = 80;

    public record DepartmentPage(List<DepartmentView> items, long total) {}

    private final DepartmentRepository departments;
    private final DepartmentSearch search;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final Clock clock;

    public DepartmentService(
            DepartmentRepository departments,
            DepartmentSearch search,
            AccessPolicy policy,
            AuditService audit,
            Clock clock) {
        this.departments = departments;
        this.search = search;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public DepartmentView create(OrgContext org, String rawName, String rawDescription) {
        policy.require(org, Permission.DEPARTMENT_MANAGE);
        String name = normalizeName(rawName);
        requireNameFree(org.organizationId(), name, NO_ONE);

        Department saved =
                save(
                        Department.create(
                                org.organizationId(),
                                name,
                                normalizeText(rawDescription),
                                clock.instant()));
        audit.record(
                AuditEvent.of(
                                AuditEventType.DEPARTMENT_CREATED,
                                org.userId(),
                                AuditTargetType.DEPARTMENT,
                                saved.getId())
                        .inOrganization(org.organizationId())
                        .withMetadata(Map.of("name", name)));
        return DepartmentView.from(saved);
    }

    @Transactional(readOnly = true)
    public DepartmentView get(OrgContext org, UUID id) {
        policy.require(org, Permission.DEPARTMENT_VIEW);
        return DepartmentView.from(find(org, id));
    }

    @Transactional(readOnly = true)
    public DepartmentPage list(
            OrgContext org,
            Boolean active,
            String query,
            DepartmentSort sort,
            boolean ascending,
            int page,
            int size) {
        policy.require(org, Permission.DEPARTMENT_VIEW);
        String trimmed = query == null ? null : query.trim();
        if (trimmed != null && trimmed.isEmpty()) {
            trimmed = null;
        }
        if (trimmed != null && trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH);
        }
        DepartmentSearch.Result result =
                search.search(
                        org.organizationId(),
                        new DepartmentSearch.Criteria(active, trimmed, sort, ascending),
                        page,
                        size);
        return new DepartmentPage(
                result.items().stream().map(DepartmentView::from).toList(), result.total());
    }

    @Transactional
    public DepartmentView update(
            OrgContext org, UUID id, String rawName, String rawDescription, long expectedVersion) {
        policy.require(org, Permission.DEPARTMENT_MANAGE);
        Department department = find(org, id);
        if (department.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "STALE_VERSION",
                    "This department was changed by someone else. Reload and try again.");
        }
        String name = normalizeName(rawName);
        String description = normalizeText(rawDescription);
        String oldName = department.getName();
        boolean nameChanged = !name.equals(oldName);
        boolean changed = nameChanged || !Objects.equals(description, department.getDescription());
        if (!changed) {
            return DepartmentView.from(department); // nothing to write, nothing to audit
        }
        if (nameChanged) {
            requireNameFree(org.organizationId(), name, id);
        }

        department.update(name, description, clock.instant());
        Department saved = save(department);
        AuditEvent event =
                AuditEvent.of(
                                AuditEventType.DEPARTMENT_UPDATED,
                                org.userId(),
                                AuditTargetType.DEPARTMENT,
                                id)
                        .inOrganization(org.organizationId());
        audit.record(
                nameChanged
                        ? event.withMetadata(Map.of("fromName", oldName, "toName", name))
                        : event);
        return DepartmentView.from(saved);
    }

    /** Idempotent: deactivating an inactive department changes nothing. */
    @Transactional
    public DepartmentView setActive(OrgContext org, UUID id, boolean active) {
        policy.require(org, Permission.DEPARTMENT_MANAGE);
        Department department = find(org, id);
        if (department.isActive() == active) {
            return DepartmentView.from(department);
        }
        Instant now = clock.instant();
        if (active) {
            department.reactivate(now);
        } else {
            department.deactivate(now);
        }
        Department saved = save(department);
        audit.record(
                AuditEvent.of(
                                active
                                        ? AuditEventType.DEPARTMENT_REACTIVATED
                                        : AuditEventType.DEPARTMENT_DEACTIVATED,
                                org.userId(),
                                AuditTargetType.DEPARTMENT,
                                id)
                        .inOrganization(org.organizationId()));
        return DepartmentView.from(saved);
    }

    private Department find(OrgContext org, UUID id) {
        return departments
                .findByIdAndOrganizationId(id, org.organizationId())
                .orElseThrow(NotFoundException::new);
    }

    private void requireNameFree(UUID organizationId, String name, UUID excludeId) {
        if (departments.nameTaken(organizationId, name, excludeId)) {
            throw nameTaken();
        }
    }

    /** Flushes now so a unique-index violation (a race) surfaces here and becomes the same 409. */
    private Department save(Department department) {
        try {
            return departments.saveAndFlush(department);
        } catch (DataIntegrityViolationException e) {
            String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
            if (message != null && message.contains(NAME_CONSTRAINT)) {
                throw nameTaken();
            }
            throw e;
        }
    }

    private static ConflictException nameTaken() {
        return new ConflictException(
                "DEPARTMENT_NAME_TAKEN", "A department with this name already exists.");
    }

    private static String normalizeName(String raw) {
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Department name must not be blank");
        }
        return name;
    }

    private static String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        return text.isEmpty() ? null : text;
    }
}
