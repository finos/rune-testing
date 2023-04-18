package com.regnosys.testing;

import org.junit.jupiter.api.Assertions;

import java.util.Objects;
import java.util.Optional;

public class WhitespaceAgnosticAssert {
    public static void assertEquals(String expected, String actual) {
        Assertions.assertEquals(normalize(expected), normalize(actual));
    }

    public static void assertEquals(String expected, String actual, String message) {
        Assertions.assertEquals(normalize(expected), normalize(actual), message);
    }

    public static boolean equals(String s1, String s2) {
        return Objects.equals(normalize(s1), normalize(s2));
    }

    private static String normalize(String str) {
        return Optional.ofNullable(str)
                .map(s -> s.replaceAll("\\s+", ""))
                .orElse(null);
    }
}
