package com.l2c.nexus.request.api;

import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.web.CurrentOrg;
import com.l2c.nexus.request.application.ServiceRequestService;
import com.l2c.nexus.request.application.ServiceRequestService.ListCriteria;
import com.l2c.nexus.request.application.ServiceRequestService.RequestInput;
import com.l2c.nexus.request.application.ServiceRequestService.RequestPage;
import com.l2c.nexus.request.domain.RequestCategory;
import com.l2c.nexus.request.domain.RequestSort;
import com.l2c.nexus.request.domain.RequestStatus;
import com.l2c.nexus.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

    RequestController(ServiceRequestService service) {
        this.service = service;
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
                                query),
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
        return RequestDetailResponse.from(service.create(org, input(payload)), org.membershipId());
    }

    @GetMapping("/{requestId}")
    RequestDetailResponse get(@CurrentOrg OrgContext org, @PathVariable UUID requestId) {
        return RequestDetailResponse.from(service.get(org, requestId), org.membershipId());
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
        return RequestDetailResponse.from(
                service.update(org, requestId, input, payload.version()), org.membershipId());
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
