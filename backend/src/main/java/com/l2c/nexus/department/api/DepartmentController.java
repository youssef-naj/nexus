package com.l2c.nexus.department.api;

import com.l2c.nexus.department.application.DepartmentService;
import com.l2c.nexus.department.application.DepartmentService.DepartmentPage;
import com.l2c.nexus.department.domain.DepartmentSort;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.web.CurrentOrg;
import com.l2c.nexus.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/orgs/{orgId}/departments")
class DepartmentController {

    private final DepartmentService departments;

    DepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    /** sort is NAME (default) or CREATED; direction is ASC (default) or DESC. */
    @GetMapping
    PageResponse<DepartmentResponse> list(
            @CurrentOrg OrgContext org,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "NAME") DepartmentSort sort,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.min(Math.max(page, 0), 100_000);
        int safeSize = Math.min(Math.max(size, 1), 100);
        DepartmentPage result =
                departments.list(
                        org, active, query, sort, direction.isAscending(), safePage, safeSize);
        List<DepartmentResponse> content =
                result.items().stream().map(DepartmentResponse::from).toList();
        return PageResponse.of(content, safePage, safeSize, result.total());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DepartmentResponse create(
            @CurrentOrg OrgContext org, @Valid @RequestBody DepartmentRequest request) {
        return DepartmentResponse.from(
                departments.create(org, request.name(), request.description()));
    }

    @GetMapping("/{departmentId}")
    DepartmentResponse get(@CurrentOrg OrgContext org, @PathVariable UUID departmentId) {
        return DepartmentResponse.from(departments.get(org, departmentId));
    }

    @PutMapping("/{departmentId}")
    DepartmentResponse update(
            @CurrentOrg OrgContext org,
            @PathVariable UUID departmentId,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return DepartmentResponse.from(
                departments.update(
                        org,
                        departmentId,
                        request.name(),
                        request.description(),
                        request.version()));
    }

    @PostMapping("/{departmentId}/deactivate")
    DepartmentResponse deactivate(@CurrentOrg OrgContext org, @PathVariable UUID departmentId) {
        return DepartmentResponse.from(departments.setActive(org, departmentId, false));
    }

    @PostMapping("/{departmentId}/reactivate")
    DepartmentResponse reactivate(@CurrentOrg OrgContext org, @PathVariable UUID departmentId) {
        return DepartmentResponse.from(departments.setActive(org, departmentId, true));
    }
}
