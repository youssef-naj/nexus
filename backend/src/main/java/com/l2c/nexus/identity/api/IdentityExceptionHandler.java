package com.l2c.nexus.identity.api;

import com.l2c.nexus.identity.application.InvalidTokenException;
import com.l2c.nexus.identity.application.WeakPasswordException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdentityExceptionHandler {

    @ExceptionHandler(WeakPasswordException.class)
    ProblemDetail handleWeakPassword(WeakPasswordException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");
        problem.setProperty("errors", Map.of("password", ex.getViolations()));
        return problem;
    }

    @ExceptionHandler(InvalidTokenException.class)
    ProblemDetail handleInvalidToken(InvalidTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid or expired token");
        problem.setDetail("This link is invalid or has expired.");
        return problem;
    }
}
