package com.l2c.nexus.dashboard.api;

import com.l2c.nexus.dashboard.application.DashboardService;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.web.CurrentOrg;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant route: the gate has already proven the caller belongs to {orgId}. */
@RestController
@RequestMapping("/api/orgs/{orgId}/dashboard")
class DashboardController {

    private final DashboardService dashboard;

    DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    DashboardResponse get(@CurrentOrg OrgContext org) {
        return DashboardResponse.from(dashboard.load(org));
    }
}
