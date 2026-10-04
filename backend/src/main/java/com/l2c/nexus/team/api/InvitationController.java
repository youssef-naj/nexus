package com.l2c.nexus.team.api;

import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.web.CurrentOrg;
import com.l2c.nexus.team.application.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tenant routes: the gate has already proven the caller belongs to {orgId}. */
@RestController
@RequestMapping("/api/orgs/{orgId}/invitations")
class InvitationController {

    private final InvitationService invitations;

    InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InvitationResponse create(
            @CurrentOrg OrgContext org, @Valid @RequestBody InvitationRequest request) {
        return InvitationResponse.from(invitations.create(org, request.email(), request.role()));
    }

    @GetMapping
    List<InvitationResponse> pending(@CurrentOrg OrgContext org) {
        return invitations.listPending(org).stream().map(InvitationResponse::from).toList();
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@CurrentOrg OrgContext org, @PathVariable UUID invitationId) {
        invitations.revoke(org, invitationId);
    }
}
