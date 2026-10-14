package com.l2c.nexus.platform.api;

import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.platform.application.PlatformAdmin;
import com.l2c.nexus.platform.application.PlatformAuditService;
import com.l2c.nexus.platform.application.PlatformAuditService.AuditPage;
import com.l2c.nexus.platform.application.PlatformAuditService.Entry;
import com.l2c.nexus.platform.web.CurrentPlatformAdmin;
import com.l2c.nexus.shared.web.PageResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/audit")
class PlatformAuditController {

    record EntryResponse(
            UUID id,
            String eventType,
            String actorName,
            String targetType,
            String targetName,
            Map<String, String> metadata,
            Instant occurredAt) {

        static EntryResponse from(Entry entry) {
            return new EntryResponse(
                    entry.id(),
                    entry.eventType(),
                    entry.actorName(),
                    entry.targetType(),
                    entry.targetName(),
                    entry.metadata(),
                    entry.occurredAt());
        }
    }

    private final PlatformAuditService audit;

    PlatformAuditController(PlatformAuditService audit) {
        this.audit = audit;
    }

    /** Newest first. Only PLATFORM_* events appear. */
    @GetMapping
    PageResponse<EntryResponse> list(
            @CurrentPlatformAdmin PlatformAdmin admin,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.min(Math.max(page, 0), 100_000);
        int safeSize = Math.min(Math.max(size, 1), 100);
        AuditPage result = audit.list(admin, eventType, safePage, safeSize);
        return PageResponse.of(
                result.items().stream().map(EntryResponse::from).toList(),
                safePage,
                safeSize,
                result.total());
    }
}
