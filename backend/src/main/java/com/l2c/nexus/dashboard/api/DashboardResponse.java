package com.l2c.nexus.dashboard.api;

import com.l2c.nexus.dashboard.application.DashboardService.Dashboard;
import com.l2c.nexus.dashboard.application.DashboardService.Scope;
import com.l2c.nexus.dashboard.application.DashboardService.Summary;
import com.l2c.nexus.request.application.RequestStatistics.RecentEvent;
import com.l2c.nexus.request.domain.RequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "scope" says what total and byStatus cover (the whole organization, or only your own requests).
 * awaitingReview and assignedToMe are null for people who cannot review. "mine" is always your own
 * requests.
 */
public record DashboardResponse(
        Scope scope,
        long total,
        Map<RequestStatus, Long> byStatus,
        Long awaitingReview,
        Long assignedToMe,
        Mine mine,
        List<RecentResponse> recent) {

    public record Mine(long total, Map<RequestStatus, Long> byStatus) {}

    public record RecentResponse(
            UUID id,
            UUID requestId,
            String reference,
            String title,
            String action,
            RequestStatus toStatus,
            String actorName,
            String targetName,
            Instant occurredAt) {}

    static DashboardResponse from(Dashboard dashboard) {
        Summary overall = dashboard.overall();
        return new DashboardResponse(
                dashboard.scope(),
                overall.total(),
                overall.byStatus(),
                dashboard.awaitingReview(),
                dashboard.assignedToMe(),
                new Mine(dashboard.mine().total(), dashboard.mine().byStatus()),
                dashboard.recent().stream().map(DashboardResponse::recent).toList());
    }

    private static RecentResponse recent(RecentEvent event) {
        return new RecentResponse(
                event.id(),
                event.requestId(),
                event.reference(),
                event.title(),
                event.action(),
                event.toStatus(),
                event.actorName(),
                event.targetName(),
                event.occurredAt());
    }
}
