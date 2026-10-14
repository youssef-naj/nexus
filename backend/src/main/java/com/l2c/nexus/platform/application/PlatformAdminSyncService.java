package com.l2c.nexus.platform.application;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import com.l2c.nexus.identity.application.PlatformAdminRegistry;
import com.l2c.nexus.identity.application.UserSummary;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes the set of platform administrators equal to the configured list (ADR-0032): listed,
 * verified, active accounts are granted; everyone else loses the role. There is no API that grants
 * the role, so nobody can promote themselves. Every change is audited, with no personal data.
 */
@Service
public class PlatformAdminSyncService {

    /** missing: listed addresses that have no verified, active account (yet). */
    public record SyncResult(int granted, int revoked, int missing) {}

    private final PlatformAdminRegistry registry;
    private final AuditService audit;

    public PlatformAdminSyncService(PlatformAdminRegistry registry, AuditService audit) {
        this.registry = registry;
        this.audit = audit;
    }

    @Transactional
    public SyncResult sync(Set<String> desiredEmails) {
        Set<UUID> desired = new HashSet<>();
        int missing = 0;
        for (String email : desiredEmails) {
            Optional<UserSummary> eligible = registry.eligibleByEmail(email);
            if (eligible.isPresent()) {
                desired.add(eligible.get().id());
            } else {
                missing++;
            }
        }

        int granted = 0;
        for (UUID id : desired) {
            if (registry.setPlatformAdmin(id, true)) {
                record(AuditEventType.PLATFORM_ADMIN_GRANTED, id);
                granted++;
            }
        }
        int revoked = 0;
        for (UUID id : registry.currentAdminIds()) {
            if (!desired.contains(id) && registry.setPlatformAdmin(id, false)) {
                record(AuditEventType.PLATFORM_ADMIN_REVOKED, id);
                revoked++;
            }
        }
        return new SyncResult(granted, revoked, missing);
    }

    private void record(AuditEventType type, UUID userId) {
        // No actor (the configuration is the actor), no organization, no metadata
        audit.record(AuditEvent.of(type, null, AuditTargetType.USER, userId));
    }
}
