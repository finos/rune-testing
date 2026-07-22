package com.regnosys.testing.pipeline;

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
import com.regnosys.rosetta.common.transform.FunctionNameHelper;
import org.finos.rune.mapper.RuneJsonObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies {@link PipelineTestPackWriter} resolves its default JSON mapper through
 * {@link com.regnosys.testing.serialisation.DefaultModelSerialisation} rather than the previously
 * hard-coded legacy mapper — the same wiring {@link com.regnosys.testing.transform.TransformTestExtension}
 * got in Step T1.
 * <p>
 * This module's own test classpath carries no {@code rune-config.yml}/{@code rosetta-config.yml}, so it
 * exercises (and guards) the backward-compatible leg: a model that does not opt in keeps today's legacy
 * behaviour, unchanged. The {@code RUNE_JSON} leg is covered directly against the resolver in
 * {@code DefaultModelSerialisationTest}, which controls the classloader; {@link PipelineTestPackWriter}
 * always resolves from {@code this.getClass().getClassLoader()}, so it is not independently overridable
 * from a test in the same module.
 */
class PipelineTestPackWriterDefaultSerialisationTest {

    @Test
    void defaultsToLegacyMapperWhenNoModelConfigIsOnTheClasspath() throws Exception {
        PipelineTestPackWriter writer = new PipelineTestPackWriter(
                Mockito.mock(PipelineTreeBuilder.class),
                Mockito.mock(PipelineFunctionRunnerProvider.class),
                Mockito.mock(PipelineModelBuilder.class),
                Mockito.mock(FunctionNameHelper.class));

        ObjectMapper defaultJsonObjectMapper = getField(writer, "defaultJsonObjectMapper");

        assertFalse(defaultJsonObjectMapper instanceof RuneJsonObjectMapper);
    }

    private ObjectMapper getField(PipelineTestPackWriter target, String fieldName) throws Exception {
        Field field = PipelineTestPackWriter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (ObjectMapper) field.get(target);
    }
}
