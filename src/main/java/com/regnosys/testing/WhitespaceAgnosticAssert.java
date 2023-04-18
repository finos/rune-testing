package com.regnosys.testing;

import org.junit.jupiter.api.Assertions;

import java.util.Objects;
import java.util.Optional;

public class WhitespaceAgnosticAssert {
    public static void assertEquals(String s1, String s2) {
        Assertions.assertEquals(normalize(s1), normalize(s2));
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
