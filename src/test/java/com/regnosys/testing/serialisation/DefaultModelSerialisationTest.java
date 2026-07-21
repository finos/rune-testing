package com.regnosys.testing.serialisation;

/*-
 * ===============
 * Rune Testing
 * ===============
 * Copyright (C) 2022 - 2026 REGnosys
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

import com.fasterxml.jackson.databind.ObjectWriter;
import org.finos.rune.mapper.RuneJsonObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultModelSerialisationTest {

    @TempDir
    Path tempDir;

    @Test
    void ruleJsonConfigResolvesToRuneJsonMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
        assertNotNull(resolved.getObjectWriter());
    }

    @Test
    void explicitJsonConfigResolvesToLegacyMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: JSON
                """);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void noConfigOnClasspathResolvesToLegacyMapper() throws MalformedURLException {
        ClassLoader classLoader = dirClassLoader();

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void configWithoutDefaultSerialisationFormatFieldResolvesToLegacyMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                """);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void malformedYamlResolvesToLegacyMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", "model: [unterminated");

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void configMissingRequiredModelKeyResolvesToLegacyMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", "unrelated: value");

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void legacyConfigFileNameIsHonouredWhenNewNameIsAbsent() throws IOException {
        ClassLoader classLoader = configClassLoader("rosetta-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void newConfigFileNameIsPreferredOverLegacyName() throws IOException {
        Files.writeString(tempDir.resolve("rune-config.yml"), """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("rosetta-config.yml"), """
                model:
                  name: Test Model
                  defaultSerialisationFormat: JSON
                """, StandardCharsets.UTF_8);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(dirClassLoader());

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void objectWriterMatchesTheResolvedMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        ObjectWriter writer = resolved.getObjectWriter();
        assertNotNull(writer);
        // Pretty-printed (rune-json's own canonical shape: 2-space indent, "\n") and round-trips through
        // the same mapper.
        String written = writer.writeValueAsString(java.util.Map.of("a", 1));
        assertTrue(written.contains("\n"));
        assertEquals(1, resolved.getObjectMapper().readTree(written).get("a").asInt());
    }

    @Test
    void resolveRejectsNullClassLoader() {
        assertThrows(NullPointerException.class, () -> DefaultModelSerialisation.resolve(null));
    }

    private ClassLoader configClassLoader(String fileName, String yaml) throws IOException {
        Files.writeString(tempDir.resolve(fileName), yaml, StandardCharsets.UTF_8);
        return dirClassLoader();
    }

    private ClassLoader dirClassLoader() throws MalformedURLException {
        return new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
    }
}
