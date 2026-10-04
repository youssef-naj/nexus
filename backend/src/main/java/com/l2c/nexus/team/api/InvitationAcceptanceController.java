package com.l2c.nexus.team.api;

import com.l2c.nexus.team.application.AcceptedInvitation;
import com.l2c.nexus.team.application.InvitationAcceptanceService;
import com.l2c.nexus.team.application.InvitationPreview;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Not a tenant route: the invitee is not a member yet, so the gate cannot apply. The token (in the
 * body, never the URL) and the signed-in account's verified email are the credentials.
 */
@RestController
@RequestMapping("/api/invitations")
class InvitationAcceptanceController {

    private final InvitationAcceptanceService acceptance;

    InvitationAcceptanceController(InvitationAcceptanceService acceptance) {
        this.acceptance = acceptance;
    }

    @PostMapping("/preview")
    InvitationPreviewResponse preview(
            Authentication authentication, @Valid @RequestBody InvitationTokenRequest request) {
        InvitationPreview preview = acceptance.preview(userId(authentication), request.token());
        return new InvitationPreviewResponse(preview.organizationName(), preview.role());
    }

    @PostMapping("/accept")
    AcceptedInvitationResponse accept(
            Authentication authentication, @Valid @RequestBody InvitationTokenRequest request) {
        AcceptedInvitation accepted = acceptance.accept(userId(authentication), request.token());
        return new AcceptedInvitationResponse(
                accepted.organizationId(), accepted.organizationName(), accepted.role());
    }

    @PostMapping("/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(Authentication authentication, @Valid @RequestBody InvitationTokenRequest request) {
        acceptance.reject(userId(authentication), request.token());
    }

    private static UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
