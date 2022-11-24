package com.regnosys.testing;

import org.junit.jupiter.api.Assertions;

public class WhitespaceAgnosticAssert {
    public static void assertEquals(String s1, String s2) {
        Assertions.assertEquals(normalize(s1), normalize(s2));
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", " ");
    }
}
