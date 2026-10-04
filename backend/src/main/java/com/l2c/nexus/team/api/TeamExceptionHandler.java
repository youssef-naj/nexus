package com.l2c.nexus.team.api;

import com.l2c.nexus.team.application.InvalidInvitationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = InvitationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE) // specific handlers go before the global catch-all
class TeamExceptionHandler {

    @ExceptionHandler(InvalidInvitationException.class)
    ProblemDetail handleInvalidInvitation(InvalidInvitationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid or expired invitation");
        problem.setDetail("This invitation is invalid or has expired.");
        return problem;
    }
}
