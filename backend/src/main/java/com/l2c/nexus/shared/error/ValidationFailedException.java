package com.l2c.nexus.shared.error;

import java.util.List;
import java.util.Map;

/**
 * Business-rule validation that a bean annotation cannot express. Rendered as 400 with field
 * errors.
 */
public class ValidationFailedException extends RuntimeException {

    private final Map<String, List<String>> errors;

    public ValidationFailedException(String field, String message) {
        this(Map.of(field, List.of(message)));
    }

    public ValidationFailedException(Map<String, List<String>> errors) {
        super("Validation failed");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, List<String>> getErrors() {
        return errors;
    }
}
