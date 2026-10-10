package com.l2c.nexus.request.application;

import com.l2c.nexus.department.application.DepartmentDirectory;
import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.membership.application.MembershipView;
import com.l2c.nexus.organization.application.AccessPolicy;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.application.Permission;
import com.l2c.nexus.organization.application.RequestNumberAllocator;
import com.l2c.nexus.request.domain.RequestCategory;
import com.l2c.nexus.request.domain.RequestSort;
import com.l2c.nexus.request.domain.RequestStatus;
import com.l2c.nexus.request.domain.ServiceRequest;
import com.l2c.nexus.request.persistence.RequestDetail;
import com.l2c.nexus.request.persistence.RequestQueries;
import com.l2c.nexus.request.persistence.RequestSummary;
import com.l2c.nexus.request.persistence.ServiceRequestRepository;
import com.l2c.nexus.shared.error.ConflictException;
import com.l2c.nexus.shared.error.ForbiddenActionException;
import com.l2c.nexus.shared.error.NotFoundException;
import com.l2c.nexus.shared.error.ValidationFailedException;
import com.l2c.nexus.shared.ratelimit.RateLimitDecision;
import com.l2c.nexus.shared.ratelimit.RateLimitExceededException;
import com.l2c.nexus.shared.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, viewing, editing and searching service requests (ADR-0026). Viewing your own requests
 * needs no permission beyond membership; REQUEST_VIEW_ALL widens it to the whole organization.
 */
@Service
public class ServiceRequestService {

    private static final int MAX_QUERY_LENGTH = 80;

    public record RequestInput(
            String title,
            String description,
            RequestCategory category,
            LocalDate dueDate,
            UUID departmentId) {}

    public record ListCriteria(
            RequestStatus status,
            RequestCategory category,
            UUID departmentId,
            UUID createdBy,
            boolean mine,
            LocalDate createdFrom,
            LocalDate createdTo,
            String query,
            boolean reviewable,
            boolean assignedToMe) {}

    public record RequestPage(List<RequestSummary> items, long total) {}

    private final ServiceRequestRepository requests;
    private final RequestQueries queries;
    private final AccessPolicy policy;
    private final MembershipService memberships;
    private final RequestNumberAllocator numbers;
    private final DepartmentDirectory departments;
    private final RateLimiter limiter;
    private final RequestRateLimits limits;
    private final Clock clock;

    public ServiceRequestService(
            ServiceRequestRepository requests,
            RequestQueries queries,
            AccessPolicy policy,
            MembershipService memberships,
            RequestNumberAllocator numbers,
            DepartmentDirectory departments,
            RateLimiter limiter,
            RequestRateLimits limits,
            Clock clock) {
        this.requests = requests;
        this.queries = queries;
        this.policy = policy;
        this.memberships = memberships;
        this.numbers = numbers;
        this.departments = departments;
        this.limiter = limiter;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional
    public RequestDetail create(OrgContext org, RequestInput input) {
        policy.require(org, Permission.REQUEST_CREATE);
        enforceRateLimit(org);
        Instant now = clock.instant();
        validateDueDate(input.dueDate(), null, LocalDate.now(clock));
        String title = normalizeTitle(input.title());

        // Allocating the number takes the organization lock. Removing a member takes the same
        // lock, so after this line the creator's membership cannot change under us.
        String reference = numbers.next(org.organizationId());
        MembershipView creator =
                memberships
                        .findActiveMembership(org.organizationId(), org.userId())
                        .orElseThrow(NotFoundException::new);
        if (input.departmentId() != null) {
            departments.requireActiveForUse(org.organizationId(), input.departmentId());
        }

        ServiceRequest saved =
                requests.saveAndFlush(
                        ServiceRequest.draft(
                                org.organizationId(),
                                reference,
                                title,
                                normalizeText(input.description()),
                                input.category(),
                                creator.id(),
                                input.departmentId(),
                                input.dueDate(),
                                now));
        return queries.find(org.organizationId(), saved.getId(), null).orElseThrow();
    }

    @Transactional(readOnly = true)
    public RequestDetail get(OrgContext org, UUID requestId) {
        UUID onlyMine = canViewAll(org) ? null : org.membershipId();
        return queries.find(org.organizationId(), requestId, onlyMine)
                .orElseThrow(NotFoundException::new);
    }

    @Transactional(readOnly = true)
    public RequestPage list(
            OrgContext org,
            ListCriteria criteria,
            RequestSort sort,
            boolean ascending,
            int page,
            int size) {
        UUID creator = criteria.mine() ? org.membershipId() : criteria.createdBy();
        if (!canViewAll(org)) {
            // Employees see only their own requests: a filter can narrow that, never widen it
            if (creator != null && !creator.equals(org.membershipId())) {
                return new RequestPage(List.of(), 0);
            }
            creator = org.membershipId();
        }
        RequestStatus status = criteria.status();
        UUID excludeCreator = null;
        UUID assignee = null;
        boolean reviewer = policy.can(org.role(), Permission.REQUEST_REVIEW);
        if (criteria.reviewable() || criteria.assignedToMe()) {
            // Both are queues of submitted requests for reviewers; a conflicting status is empty
            if (!reviewer || (status != null && status != RequestStatus.SUBMITTED)) {
                return new RequestPage(List.of(), 0);
            }
            status = RequestStatus.SUBMITTED;
        }
        if (criteria.reviewable()) {
            excludeCreator = org.membershipId(); // submitted by someone else
        }
        if (criteria.assignedToMe()) {
            assignee = org.membershipId();
        }
        String query = criteria.query() == null ? null : criteria.query().trim();
        if (query != null && query.isEmpty()) {
            query = null;
        }
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }
        RequestQueries.Filter filter =
                new RequestQueries.Filter(
                        status,
                        criteria.category(),
                        criteria.departmentId(),
                        creator,
                        query,
                        criteria.createdFrom() == null
                                ? null
                                : startOfDay(criteria.createdFrom(), 0, "createdFrom"),
                        criteria.createdTo() == null
                                ? null
                                : startOfDay(criteria.createdTo(), 1, "createdTo"),
                        excludeCreator,
                        assignee);
        RequestQueries.SummaryPage result =
                queries.search(org.organizationId(), filter, sort, ascending, page, size);
        return new RequestPage(result.items(), result.total());
    }

    @Transactional
    public RequestDetail update(
            OrgContext org, UUID requestId, RequestInput input, long expectedVersion) {
        policy.require(org, Permission.REQUEST_CREATE);
        ServiceRequest request =
                requests.findByIdAndOrganizationId(requestId, org.organizationId())
                        .orElseThrow(NotFoundException::new);

        boolean mine = request.getCreatedByMembershipId().equals(org.membershipId());
        if (!mine && !canViewAll(org)) {
            throw new NotFoundException(); // a request you may not see does not exist for you
        }
        if (!mine) {
            throw new ForbiddenActionException(
                    "NOT_REQUEST_OWNER", "Only the person who created a request can edit it.");
        }
        if (!request.getStatus().isEditable()) {
            throw new ConflictException(
                    "REQUEST_NOT_EDITABLE", "This request can no longer be edited.");
        }
        if (request.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "STALE_VERSION",
                    "This request was changed by someone else. Reload and try again.");
        }

        String title = normalizeTitle(input.title());
        String description = normalizeText(input.description());
        boolean changed =
                !title.equals(request.getTitle())
                        || !Objects.equals(description, request.getDescription())
                        || input.category() != request.getCategory()
                        || !Objects.equals(input.departmentId(), request.getDepartmentId())
                        || !Objects.equals(input.dueDate(), request.getDueDate());
        if (!changed) {
            return queries.find(org.organizationId(), requestId, null).orElseThrow();
        }
        // Rules apply only to what actually changes: a request that is already late can still be
        // retitled, and a department deactivated since can stay as it is.
        validateDueDate(input.dueDate(), request.getDueDate(), LocalDate.now(clock));
        if (input.departmentId() != null
                && !input.departmentId().equals(request.getDepartmentId())) {
            departments.requireActiveForUse(org.organizationId(), input.departmentId());
        }

        request.edit(
                title,
                description,
                input.category(),
                input.departmentId(),
                input.dueDate(),
                clock.instant());
        requests.saveAndFlush(request);
        return queries.find(org.organizationId(), requestId, null).orElseThrow();
    }

    private boolean canViewAll(OrgContext org) {
        return policy.can(org.role(), Permission.REQUEST_VIEW_ALL);
    }

    private void enforceRateLimit(OrgContext org) {
        RateLimitDecision decision =
                limiter.tryAcquire(
                        "request:create:" + org.organizationId() + ":" + org.userId(),
                        limits.requestCreatePerMember());
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }

    /** Not in the past and within ten years, unless it is the value the request already has. */
    private static void validateDueDate(LocalDate dueDate, LocalDate unchanged, LocalDate today) {
        if (dueDate == null || dueDate.equals(unchanged)) {
            return;
        }
        if (dueDate.isBefore(today)) {
            throw new ValidationFailedException("dueDate", "must not be in the past");
        }
        if (dueDate.isAfter(today.plusYears(10))) {
            throw new ValidationFailedException("dueDate", "must be within the next 10 years");
        }
    }

    private static Instant startOfDay(LocalDate date, long plusDays, String field) {
        if (date.getYear() < 1900 || date.getYear() > 2200) {
            throw new ValidationFailedException(field, "is out of range");
        }
        try {
            return date.plusDays(plusDays).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeException e) {
            throw new ValidationFailedException(field, "is out of range");
        }
    }

    private static String normalizeTitle(String raw) {
        String title = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (title.isEmpty()) {
            throw new ValidationFailedException("title", "must not be blank");
        }
        return title;
    }

    private static String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        return text.isEmpty() ? null : text;
    }
}
