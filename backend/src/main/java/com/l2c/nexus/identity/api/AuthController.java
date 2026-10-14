package com.l2c.nexus.identity.api;

import com.l2c.nexus.identity.application.EmailVerificationService;
import com.l2c.nexus.identity.application.RegisterUserCommand;
import com.l2c.nexus.identity.application.RegistrationService;
import com.l2c.nexus.identity.application.UserDirectory;
import com.l2c.nexus.identity.application.UserSummary;
import com.l2c.nexus.identity.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private static final RegistrationAcceptedResponse ACCEPTED =
            new RegistrationAcceptedResponse(
                    "Registration received. Check your email for the next steps.");

    private final RegistrationService registration;
    private final EmailVerificationService verification;
    private final UserDirectory directory;

    AuthController(
            RegistrationService registration,
            EmailVerificationService verification,
            UserDirectory directory) {
        this.registration = registration;
        this.verification = verification;
        this.directory = directory;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RegistrationAcceptedResponse register(@Valid @RequestBody RegisterRequest request) {
        // The outcome is deliberately not visible to the caller (ADR-0014).
        registration.register(
                new RegisterUserCommand(
                        request.email(), request.password(), request.displayName()));
        return ACCEPTED;
    }

    /** The token travels in the body, not the URL, so it stays out of access logs. */
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        verification.verify(request.token());
    }

    /**
     * The current user, read fresh from the database. If the account was disabled after login, the
     * session is ended here instead of continuing to work.
     */
    @GetMapping("/me")
    CurrentUserResponse me(
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        UserSummary user = directory.findActiveById(principal.id()).orElse(null);
        if (user == null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            throw new InsufficientAuthenticationException("Session is no longer valid");
        }
        return new CurrentUserResponse(
                user.id(), user.email(), user.displayName(), user.platformAdmin());
    }
}
