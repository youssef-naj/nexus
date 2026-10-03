package com.l2c.nexus.organization.application;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.membership.application.MembershipView;
import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.domain.Organization;
import com.l2c.nexus.organization.persistence.OrganizationRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private static final int SLUG_ATTEMPTS = 5;

    private final OrganizationRepository organizations;
    private final MembershipService memberships;
    private final AuditService audit;
    private final SlugGenerator slugs;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrganizationService(
            OrganizationRepository organizations,
            MembershipService memberships,
            AuditService audit,
            SlugGenerator slugs,
            Clock clock) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.audit = audit;
        this.slugs = slugs;
        this.clock = clock;
    }

    /** Creates the organization, makes the creator its Owner, and audits it: all or nothing. */
    @Transactional
    public MyOrganization create(UUID creatorUserId, String rawName) {
        String name = rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Organization name must not be blank");
        }
        String slug = availableSlug(slugs.baseFrom(name));

        Organization organization =
                organizations.save(Organization.create(name, slug, clock.instant()));
        memberships.addMember(organization.getId(), creatorUserId, OrgRole.OWNER);
        audit.record(
                AuditEvent.of(
                                AuditEventType.ORGANIZATION_CREATED,
                                creatorUserId,
                                AuditTargetType.ORGANIZATION,
                                organization.getId())
                        .inOrganization(organization.getId())
                        .withMetadata(Map.of("name", name, "slug", slug)));

        return new MyOrganization(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                OrgRole.OWNER,
                organization.getStatus());
    }

    @Transactional(readOnly = true)
    public List<MyOrganization> listFor(UUID userId) {
        List<MembershipView> mine = memberships.activeMembershipsOf(userId);
        Map<UUID, Organization> byId =
                organizations
                        .findAllById(mine.stream().map(MembershipView::organizationId).toList())
                        .stream()
                        .collect(Collectors.toMap(Organization::getId, Function.identity()));

        return mine.stream()
                .filter(membership -> byId.containsKey(membership.organizationId()))
                .map(
                        membership -> {
                            Organization org = byId.get(membership.organizationId());
                            return new MyOrganization(
                                    org.getId(),
                                    org.getName(),
                                    org.getSlug(),
                                    membership.role(),
                                    org.getStatus());
                        })
                .sorted(Comparator.comparing(MyOrganization::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The plain slug if free, otherwise the slug plus 8 random hex characters. */
    private String availableSlug(String base) {
        if (!organizations.existsBySlug(base)) {
            return base;
        }
        for (int attempt = 0; attempt < SLUG_ATTEMPTS; attempt++) {
            byte[] bytes = new byte[4];
            random.nextBytes(bytes);
            String candidate = base + "-" + HexFormat.of().formatHex(bytes);
            if (!organizations.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free slug for " + base);
    }

    @Transactional(readOnly = true)
    public OrganizationInfo info(UUID organizationId) {
        Organization org =
                organizations
                        .findById(organizationId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Organization vanished after the gate: "
                                                        + organizationId));
        return new OrganizationInfo(org.getId(), org.getName(), org.getSlug(), org.getStatus());
    }
}
