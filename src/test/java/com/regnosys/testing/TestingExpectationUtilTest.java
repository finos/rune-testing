package com.regnosys.testing;

/*-
 * ===============
 * Rune Testing
 * ===============
 * Copyright (C) 2022 - 2024 REGnosys
 * ===============
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ===============
 */

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestingExpectationUtilTest {

    private static final String LF_EXPECTATION = "{\n  \"a\" : 1\n}";
    private static final String CRLF_EXPECTATION = "{\r\n  \"a\" : 1\r\n}";

    /**
     * An expectation file checked out on Windows (CRLF) has to compare equal to the
     * same file checked out on Linux (LF), because the output it is compared against
     * is always produced with "\n".
     */
    @Test
    void expectationReadFromResourcesIsNormalisedToLf() throws Exception {
        Path crlfResource = writeToTargetTestClasses("crlf-expectation.json", CRLF_EXPECTATION);
        try {
            String read = TestingExpectationUtil.readStringFromResources(Path.of("crlf-expectation.json"));
            assertEquals(LF_EXPECTATION, read);
        } finally {
            Files.deleteIfExists(crlfResource);
        }
    }

    @Test
    void missingResourceStillReadsAsNull() {
        assertNull(TestingExpectationUtil.readStringFromResources(Path.of("does-not-exist.json")));
    }

    @Test
    void assertJsonEqualsAcceptsEitherLineEnding() {
        TestingExpectationUtil.assertJsonEquals(CRLF_EXPECTATION, LF_EXPECTATION);
        TestingExpectationUtil.assertJsonEquals(LF_EXPECTATION, CRLF_EXPECTATION);
    }

    private static Path writeToTargetTestClasses(String fileName, String content) throws Exception {
        Path classesDir = Path.of(
                TestingExpectationUtilTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path file = classesDir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
