package com.l2c.nexus.dashboard.application;

import com.l2c.nexus.organization.application.AccessPolicy;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.application.Permission;
import com.l2c.nexus.request.application.RequestStatistics;
import com.l2c.nexus.request.application.RequestStatistics.RecentEvent;
import com.l2c.nexus.request.domain.RequestStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The organization dashboard (ADR-0030). Visibility follows the request list: everyone sees their
 * own requests, and REQUEST_VIEW_ALL widens the figures to the whole organization.
 */
@Service
public class DashboardService {

    private static final int RECENT_LIMIT = 8;

    public enum Scope {
        ORGANIZATION,
        MINE
    }

    public record Summary(long total, Map<RequestStatus, Long> byStatus) {}

    public record Dashboard(
            Scope scope,
            Summary overall,
            Long awaitingReview,
            Long assignedToMe,
            Summary mine,
            List<RecentEvent> recent) {}

    private final RequestStatistics statistics;
    private final AccessPolicy policy;

    public DashboardService(RequestStatistics statistics, AccessPolicy policy) {
        this.statistics = statistics;
        this.policy = policy;
    }

    /**
     * REPEATABLE READ: all the queries below see one snapshot, so the total always equals the sum
     * of the statuses even while requests are changing.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Dashboard load(OrgContext org) {
        policy.require(org, Permission.ORGANIZATION_VIEW);
        boolean viewAll = policy.can(org.role(), Permission.REQUEST_VIEW_ALL);
        boolean reviewer = policy.can(org.role(), Permission.REQUEST_REVIEW);
        UUID me = org.membershipId();

        Map<RequestStatus, Long> mine = statistics.countByStatus(org.organizationId(), me);
        Map<RequestStatus, Long> overall =
                viewAll ? statistics.countByStatus(org.organizationId(), null) : mine;
        Long awaiting = reviewer ? statistics.countAwaitingReview(org.organizationId(), me) : null;
        Long assigned = reviewer ? statistics.countAssignedOpenTo(org.organizationId(), me) : null;
        List<RecentEvent> recent =
                statistics.recentEvents(org.organizationId(), viewAll ? null : me, RECENT_LIMIT);

        return new Dashboard(
                viewAll ? Scope.ORGANIZATION : Scope.MINE,
                summary(overall),
                awaiting,
                assigned,
                summary(mine),
                recent);
    }

    private static Summary summary(Map<RequestStatus, Long> byStatus) {
        return new Summary(byStatus.values().stream().mapToLong(Long::longValue).sum(), byStatus);
    }
}
