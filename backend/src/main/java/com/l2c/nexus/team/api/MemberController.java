package com.l2c.nexus.team.api;

import com.l2c.nexus.membership.application.MembershipView;
import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.web.CurrentOrg;
import com.l2c.nexus.shared.web.PageResponse;
import com.l2c.nexus.team.application.MemberAdminService;
import com.l2c.nexus.team.application.MemberDirectoryService;
import com.l2c.nexus.team.application.MemberDirectoryService.MemberPage;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tenant routes: the gate has already proven the caller belongs to {orgId}. */
@RestController
@RequestMapping("/api/orgs/{orgId}")
class MemberController {

    private final MemberDirectoryService directory;
    private final MemberAdminService admin;

    MemberController(MemberDirectoryService directory, MemberAdminService admin) {
        this.directory = directory;
        this.admin = admin;
    }

    @GetMapping("/members")
    PageResponse<MemberResponse> list(
            @CurrentOrg OrgContext org,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrgRole role) {
        int safePage = Math.min(Math.max(page, 0), 100_000);
        int safeSize = Math.min(Math.max(size, 1), 100);
        MemberPage result = directory.list(org, role, safePage, safeSize);
        List<MemberResponse> content =
                result.items().stream()
                        .map(item -> MemberResponse.from(item, org.userId()))
                        .toList();
        return PageResponse.of(content, safePage, safeSize, result.total());
    }

    @PatchMapping("/members/{membershipId}")
    RoleChangeResponse changeRole(
            @CurrentOrg OrgContext org,
            @PathVariable UUID membershipId,
            @Valid @RequestBody ChangeRoleRequest request) {
        MembershipView updated =
                admin.changeRole(org, membershipId, request.role(), request.version());
        return new RoleChangeResponse(updated.id(), updated.role(), updated.version());
    }

    @DeleteMapping("/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@CurrentOrg OrgContext org, @PathVariable UUID membershipId) {
        admin.remove(org, membershipId);
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@CurrentOrg OrgContext org) {
        admin.leave(org);
    }
}
