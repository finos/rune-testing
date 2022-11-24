package com.regnosys.testing;

import java.util.List;

public class ValidationReport {
    private final boolean passed;
    private final List<String> errors;

    public ValidationReport(boolean passed, List<String> errors) {
        this.passed = passed;
        this.errors = errors;
    }

    public boolean getPassed() {
        return passed;
    }

    public List<String> getErrors() {
        return this.errors;
    }
}
