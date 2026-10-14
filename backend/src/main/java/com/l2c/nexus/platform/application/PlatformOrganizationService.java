package com.l2c.nexus.platform.application;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import com.l2c.nexus.organization.application.OrganizationAdminView;
import com.l2c.nexus.organization.application.OrganizationCatalog;
import com.l2c.nexus.organization.application.OrganizationService;
import com.l2c.nexus.organization.application.StatusChange;
import com.l2c.nexus.organization.application.StatusChangeReason;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a platform administrator may do with organizations: look at metadata, suspend, reactivate.
 */
@Service
public class PlatformOrganizationService {

    private final OrganizationCatalog catalog;
    private final OrganizationService organizations;
    private final AuditService audit;

    public PlatformOrganizationService(
            OrganizationCatalog catalog, OrganizationService organizations, AuditService audit) {
        this.catalog = catalog;
        this.organizations = organizations;
        this.audit = audit;
    }

    public OrganizationCatalog.Page search(
            PlatformAdmin admin, OrganizationCatalog.Criteria criteria, int page, int size) {
        return catalog.search(criteria, page, size);
    }

    @Transactional
    public OrganizationAdminView suspend(
            PlatformAdmin admin, UUID organizationId, long version, StatusChangeReason reason) {
        return change(admin, organizationId, OrganizationStatus.SUSPENDED, version, reason);
    }

    @Transactional
    public OrganizationAdminView reactivate(
            PlatformAdmin admin, UUID organizationId, long version, StatusChangeReason reason) {
        return change(admin, organizationId, OrganizationStatus.ACTIVE, version, reason);
    }

    private OrganizationAdminView change(
            PlatformAdmin admin,
            UUID organizationId,
            OrganizationStatus target,
            long version,
            StatusChangeReason reason) {
        StatusChange result = organizations.changeStatus(organizationId, target, version, reason);
        if (result.changed()) {
            // The platform's own record: who acted, on which organization, and why. It belongs to
            // no organization, so only the platform audit view shows it.
            audit.record(
                    AuditEvent.of(
                                    target == OrganizationStatus.SUSPENDED
                                            ? AuditEventType.PLATFORM_ORGANIZATION_SUSPENDED
                                            : AuditEventType.PLATFORM_ORGANIZATION_REACTIVATED,
                                    admin.userId(),
                                    AuditTargetType.ORGANIZATION,
                                    organizationId)
                            .withMetadata(Map.of("reason", reason.name())));
        }
        return result.organization();
    }
}
