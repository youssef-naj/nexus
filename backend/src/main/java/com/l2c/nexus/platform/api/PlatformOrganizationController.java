package com.l2c.nexus.platform.api;

import com.l2c.nexus.organization.application.OrganizationCatalog;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import com.l2c.nexus.platform.application.PlatformAdmin;
import com.l2c.nexus.platform.application.PlatformOrganizationService;
import com.l2c.nexus.platform.web.CurrentPlatformAdmin;
import com.l2c.nexus.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform routes: the platform gate has already proven the caller holds the platform role. */
@RestController
@RequestMapping("/api/platform/organizations")
class PlatformOrganizationController {

    private final PlatformOrganizationService organizations;

    PlatformOrganizationController(PlatformOrganizationService organizations) {
        this.organizations = organizations;
    }

    /** sort is NAME (default) or CREATED; direction is ASC (default) or DESC. */
    @GetMapping
    PageResponse<PlatformOrganizationResponse> list(
            @CurrentPlatformAdmin PlatformAdmin admin,
            @RequestParam(required = false) OrganizationStatus status,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "NAME") OrganizationCatalog.Sort sort,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.min(Math.max(page, 0), 100_000);
        int safeSize = Math.min(Math.max(size, 1), 100);
        OrganizationCatalog.Page result =
                organizations.search(
                        admin,
                        new OrganizationCatalog.Criteria(
                                status, query, sort, direction.isAscending()),
                        safePage,
                        safeSize);
        return PageResponse.of(
                result.items().stream().map(PlatformOrganizationResponse::from).toList(),
                safePage,
                safeSize,
                result.total());
    }

    @PostMapping("/{organizationId}/suspend")
    PlatformOrganizationResponse suspend(
            @CurrentPlatformAdmin PlatformAdmin admin,
            @PathVariable UUID organizationId,
            @Valid @RequestBody StatusChangePayload payload) {
        return PlatformOrganizationResponse.from(
                organizations.suspend(admin, organizationId, payload.version(), payload.reason()));
    }

    @PostMapping("/{organizationId}/reactivate")
    PlatformOrganizationResponse reactivate(
            @CurrentPlatformAdmin PlatformAdmin admin,
            @PathVariable UUID organizationId,
            @Valid @RequestBody StatusChangePayload payload) {
        return PlatformOrganizationResponse.from(
                organizations.reactivate(
                        admin, organizationId, payload.version(), payload.reason()));
    }
}
