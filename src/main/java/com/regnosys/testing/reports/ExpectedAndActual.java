package com.regnosys.testing.reports;

import java.nio.file.Path;

@Deprecated // is this used?
public class ExpectedAndActual<T> {

    private final Path expectationPath;
    private final T expected;
    private final T actual;

    public ExpectedAndActual(Path expectationPath, T expected, T actual) {
        this.expectationPath = expectationPath;
        this.expected = expected;
        this.actual = actual;
    }

    public Path getExpectationPath() {
        return expectationPath;
    }

    public T getExpected() {
        return expected;
    }

    public T getActual() {
        return actual;
    }
}
