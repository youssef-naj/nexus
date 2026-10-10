package com.l2c.nexus.request.api;

import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.web.CurrentOrg;
import com.l2c.nexus.request.application.RequestAssignmentService;
import com.l2c.nexus.request.application.RequestWorkflowService;
import com.l2c.nexus.request.application.ServiceRequestService;
import com.l2c.nexus.request.application.ServiceRequestService.ListCriteria;
import com.l2c.nexus.request.application.ServiceRequestService.RequestInput;
import com.l2c.nexus.request.application.ServiceRequestService.RequestPage;
import com.l2c.nexus.request.domain.RequestCategory;
import com.l2c.nexus.request.domain.RequestSort;
import com.l2c.nexus.request.domain.RequestStatus;
import com.l2c.nexus.request.persistence.RequestDetail;
import com.l2c.nexus.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tenant routes: the gate has already proven the caller belongs to {orgId}. */
@RestController
@RequestMapping("/api/orgs/{orgId}/requests")
class RequestController {

    private final ServiceRequestService service;
    private final RequestWorkflowService workflow;
    private final RequestAssignmentService assignments;

    RequestController(
            ServiceRequestService service,
            RequestWorkflowService workflow,
            RequestAssignmentService assignments) {
        this.service = service;
        this.workflow = workflow;
        this.assignments = assignments;
    }

    /** sort is CREATED (default), UPDATED or DUE_DATE; direction is DESC (default) or ASC. */
    @GetMapping
    PageResponse<RequestSummaryResponse> list(
            @CurrentOrg OrgContext org,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestCategory category,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "false") boolean reviewable,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate createdTo,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "CREATED") RequestSort sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.min(Math.max(page, 0), 100_000);
        int safeSize = Math.min(Math.max(size, 1), 100);
        RequestPage result =
                service.list(
                        org,
                        new ListCriteria(
                                status,
                                category,
                                departmentId,
                                createdBy,
                                mine,
                                createdFrom,
                                createdTo,
                                query,
                                reviewable,
                                assignedToMe),
                        sort,
                        direction.isAscending(),
                        safePage,
                        safeSize);
        return PageResponse.of(
                result.items().stream().map(RequestSummaryResponse::from).toList(),
                safePage,
                safeSize,
                result.total());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RequestDetailResponse create(
            @CurrentOrg OrgContext org, @Valid @RequestBody RequestPayload payload) {
        return respond(org, service.create(org, input(payload)));
    }

    @GetMapping("/{requestId}")
    RequestDetailResponse get(@CurrentOrg OrgContext org, @PathVariable UUID requestId) {
        return respond(org, service.get(org, requestId));
    }

    @PutMapping("/{requestId}")
    RequestDetailResponse update(
            @CurrentOrg OrgContext org,
            @PathVariable UUID requestId,
            @Valid @RequestBody UpdateRequestPayload payload) {
        RequestInput input =
                new RequestInput(
                        payload.title(),
                        payload.description(),
                        payload.category(),
                        payload.dueDate(),
                        payload.departmentId());
        return respond(org, service.update(org, requestId, input, payload.version()));
    }

    /** Submit, approve, reject or request changes. The version must be the one the user saw. */
    @PostMapping("/{requestId}/transitions")
    RequestDetailResponse transition(
            @CurrentOrg OrgContext org,
            @PathVariable UUID requestId,
            @Valid @RequestBody TransitionPayload payload) {
        return respond(
                org,
                workflow.transition(
                        org, requestId, payload.action(), payload.version(), payload.comment()));
    }

    /** Assigns a submitted request to a reviewer. Assignment is advisory (ADR-0031). */
    @PutMapping("/{requestId}/assignee")
    RequestDetailResponse assign(
            @CurrentOrg OrgContext org,
            @PathVariable UUID requestId,
            @Valid @RequestBody AssigneePayload payload) {
        return respond(
                org, assignments.assign(org, requestId, payload.membershipId(), payload.version()));
    }

    @DeleteMapping("/{requestId}/assignee")
    RequestDetailResponse unassign(
            @CurrentOrg OrgContext org, @PathVariable UUID requestId, @RequestParam long version) {
        return respond(org, assignments.unassign(org, requestId, version));
    }

    @GetMapping("/{requestId}/events")
    List<RequestEventResponse> events(@CurrentOrg OrgContext org, @PathVariable UUID requestId) {
        return workflow.history(org, requestId).stream().map(RequestEventResponse::from).toList();
    }

    private RequestDetailResponse respond(OrgContext org, RequestDetail detail) {
        return RequestDetailResponse.from(
                detail,
                org.membershipId(),
                workflow.availableActions(org, detail),
                assignments.canAssign(org, detail));
    }

    private static RequestInput input(RequestPayload payload) {
        return new RequestInput(
                payload.title(),
                payload.description(),
                payload.category(),
                payload.dueDate(),
                payload.departmentId());
    }
}
