package com.l2c.nexus.department.api;

import com.l2c.nexus.department.application.DepartmentMemberService;
import com.l2c.nexus.department.persistence.DepartmentMemberRepository.MemberRowPage;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.web.CurrentOrg;
import com.l2c.nexus.shared.web.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tenant routes: the gate has already proven the caller belongs to {orgId}. */
@RestController
@RequestMapping("/api/orgs/{orgId}")
class DepartmentMemberController {

    private final DepartmentMemberService service;

    DepartmentMemberController(DepartmentMemberService service) {
        this.service = service;
    }

    @GetMapping("/departments/{departmentId}/members")
    PageResponse<DepartmentMemberResponse> members(
            @CurrentOrg OrgContext org,
            @PathVariable UUID departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.min(Math.max(page, 0), 100_000);
        int safeSize = Math.min(Math.max(size, 1), 100);
        MemberRowPage result = service.members(org, departmentId, safePage, safeSize);
        List<DepartmentMemberResponse> content =
                result.items().stream().map(DepartmentMemberResponse::from).toList();
        return PageResponse.of(content, safePage, safeSize, result.total());
    }

    @PutMapping("/departments/{departmentId}/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void assign(
            @CurrentOrg OrgContext org,
            @PathVariable UUID departmentId,
            @PathVariable UUID membershipId) {
        service.assign(org, departmentId, membershipId);
    }

    @DeleteMapping("/departments/{departmentId}/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unassign(
            @CurrentOrg OrgContext org,
            @PathVariable UUID departmentId,
            @PathVariable UUID membershipId) {
        service.unassign(org, departmentId, membershipId);
    }

    @GetMapping("/members/{membershipId}/departments")
    List<MemberDepartmentResponse> departmentsOf(
            @CurrentOrg OrgContext org, @PathVariable UUID membershipId) {
        return service.departmentsOf(org, membershipId).stream()
                .map(MemberDepartmentResponse::from)
                .toList();
    }
}
