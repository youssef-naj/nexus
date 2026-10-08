package com.l2c.nexus.team.api;

import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.web.CurrentOrg;
import com.l2c.nexus.shared.web.PageResponse;
import com.l2c.nexus.team.application.AuditViewerService;
import com.l2c.nexus.team.application.AuditViewerService.AuditPage;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant route: the gate has already proven the caller belongs to {orgId}; AUDIT_VIEW is checked
 * next.
 */
@RestController
@RequestMapping("/api/orgs/{orgId}/audit")
class AuditController {

    private final AuditViewerService viewer;

    AuditController(AuditViewerService viewer) {
        this.viewer = viewer;
    }

    /** Newest first. from and to are inclusive calendar days (UTC). */
    @GetMapping
    PageResponse<AuditEntryResponse> list(
            @CurrentOrg OrgContext org,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.min(Math.max(page, 0), 100_000);
        int safeSize = Math.min(Math.max(size, 1), 100);
        AuditPage result = viewer.list(org, eventType, from, to, safePage, safeSize);
        return PageResponse.of(
                result.items().stream().map(AuditEntryResponse::from).toList(),
                safePage,
                safeSize,
                result.total());
    }
}
