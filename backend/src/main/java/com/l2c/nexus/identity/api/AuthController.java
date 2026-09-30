package com.l2c.nexus.identity.api;

import com.l2c.nexus.identity.application.EmailVerificationService;
import com.l2c.nexus.identity.application.RegisterUserCommand;
import com.l2c.nexus.identity.application.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    AuthController(RegistrationService registration, EmailVerificationService verification) {
        this.registration = registration;
        this.verification = verification;
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
}
