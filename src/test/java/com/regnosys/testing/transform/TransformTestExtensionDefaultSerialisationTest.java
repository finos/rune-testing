package com.regnosys.testing.transform;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.inject.AbstractModule;
import org.finos.rune.mapper.RuneJsonObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies {@link TransformTestExtension} resolves its default JSON mapper/writer through
 * {@link com.regnosys.testing.serialisation.DefaultModelSerialisation} rather than the previously
 * hard-coded legacy mapper.
 * <p>
 * This module's own test classpath carries no {@code rune-config.yml}/{@code rosetta-config.yml}, so it
 * exercises (and guards) the backward-compatible leg: a model that does not opt in keeps today's legacy
 * behaviour, unchanged. The {@code RUNE_JSON} leg is covered directly against the resolver in
 * {@code DefaultModelSerialisationTest}, which controls the classloader; {@link TransformTestExtension}
 * always resolves from {@code this.getClass().getClassLoader()}, so it is not independently overridable
 * from a test in the same module without loading a second copy of the class under a custom classloader.
 */
class TransformTestExtensionDefaultSerialisationTest {

    @Test
    void defaultsToLegacyMapperWhenNoModelConfigIsOnTheClasspath() throws Exception {
        TransformTestExtension<Object> extension = new TransformTestExtension<>(
                new AbstractModule() {
                }, Path.of("unused"), Object.class);

        ObjectMapper defaultJsonObjectMapper = getField(extension, "defaultJsonObjectMapper", ObjectMapper.class);
        ObjectWriter jsonObjectWriter = getField(extension, "jsonObjectWriter", ObjectWriter.class);

        assertFalse(defaultJsonObjectMapper instanceof RuneJsonObjectMapper);
        assertNotNull(jsonObjectWriter);
    }

    private <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = TransformTestExtension.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
}
