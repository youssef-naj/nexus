package com.l2c.nexus.shared.web;

import com.l2c.nexus.shared.error.ConflictException;
import com.l2c.nexus.shared.error.ForbiddenActionException;
import com.l2c.nexus.shared.error.NotFoundException;
import com.l2c.nexus.shared.error.ValidationFailedException;
import com.l2c.nexus.shared.ratelimit.RateLimitExceededException;
import com.l2c.nexus.shared.security.RequestBodyTooLargeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 Problem Details body. The base class already maps Spring's
 * standard exceptions (malformed JSON, wrong method, and so on) to ProblemDetail. Feature handlers
 * run first; this one, with the catch-all, runs last.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        Map<String, List<String>> errors = new TreeMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fieldError.getField(), field -> new ArrayList<>())
                    .add(Objects.toString(fieldError.getDefaultMessage(), "Invalid value"));
        }
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Not Found");
        problem.setDetail("The requested resource was not found.");
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflict");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    @ExceptionHandler(ForbiddenActionException.class)
    ProblemDetail handleForbiddenAction(ForbiddenActionException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Forbidden");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    /** Hibernate detected that someone else changed the row between our read and our write. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflict");
        problem.setDetail("The resource was changed by someone else. Reload and try again.");
        problem.setProperty("code", "CONCURRENT_MODIFICATION");
        return problem;
    }

    @ExceptionHandler(ValidationFailedException.class)
    ProblemDetail handleValidationFailed(ValidationFailedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");
        problem.setProperty("errors", ex.getErrors());
        return problem;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setTitle("Too many requests");
        problem.setDetail("Too many attempts. Please try again later.");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(RequestBodyTooLargeException.class)
    ResponseEntity<ProblemDetail> handleTooLarge(RequestBodyTooLargeException ex) {
        return ResponseEntity.status(413).body(problemOf(Failures.TOO_LARGE, null));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        // A body cut off by the size limit can surface wrapped by the JSON reader
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof RequestBodyTooLargeException) {
                return handleExceptionInternal(
                        ex,
                        problemOf(Failures.TOO_LARGE, null),
                        headers,
                        HttpStatusCode.valueOf(413),
                        request);
            }
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) throws Exception {
        // Security exceptions must reach Spring Security's own handlers (401/403).
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        Failures.Failure failure = Failures.classify(ex);
        String incident = Failures.report(failure, ex);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(failure.status());
        if (failure.retryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(failure.retryAfterSeconds()));
        }
        return response.body(problemOf(failure, incident));
    }

    private static ProblemDetail problemOf(Failures.Failure failure, String incident) {
        ProblemDetail problem = ProblemDetail.forStatus(failure.status());
        problem.setTitle(failure.title());
        problem.setDetail(failure.detail());
        if (failure.status() >= 500 && incident != null) {
            problem.setProperty("incidentId", incident);
        }
        return problem;
    }
}
