package com.regnosys.testing.pipeline;

/*-
 * ===============
 * Rune Testing
 * ===============
 * Copyright (C) 2022 - 2025 REGnosys
 * ===============
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

import com.regnosys.rosetta.common.transform.PipelineModel;
import com.rosetta.model.lib.functions.RosettaFunction;
import com.rosetta.model.lib.transform.Enrich;
import com.rosetta.model.lib.transform.Ingest;
import com.rosetta.model.lib.transform.Projection;
import com.rosetta.model.lib.transform.SerializationFormat;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how pipeline serialisation relates to the {@code @Ingest}/{@code @Projection} annotation the
 * code generator places on the function class.
 * <p>
 * The annotation on the function class is the single source of truth. When a function carries it, the
 * generated pipeline omits the {@code inputSerialisation}/{@code outputSerialisation} block entirely (see
 * {@link PipelineModelBuilder#hasTransformAnnotation}); the block is being phased out and only the
 * deprecated config-map fallback still emits it, for legacy functions that do not carry the annotation.
 * The static helpers exercised here remain available so old pipeline JSONs keep being read for backwards
 * compatibility.
 */
class PipelineModelBuilderAnnotationTest {

    @Ingest(id = "fixml", format = SerializationFormat.XML, configPath = "xml-config/fixml-xml-config.json")
    private static class XmlSchemaIngestFunction implements RosettaFunction {
    }

    @Ingest(format = SerializationFormat.XML)
    private static class BareXmlIngestFunction implements RosettaFunction {
    }

    @Projection(format = SerializationFormat.JSON)
    private static class JsonProjectionFunction implements RosettaFunction {
    }

    @Enrich
    private static class EnrichFunction implements RosettaFunction {
    }

    private static class UnannotatedFunction implements RosettaFunction {
    }

    @Test
    void annotatedFunctionsAreAnnotationDrivenSoSerialisationBlockIsOmitted() {
        // @Ingest / @Projection functions are annotation-driven: the builder writes no serialisation block.
        assertTrue(PipelineModelBuilder.hasTransformAnnotation(XmlSchemaIngestFunction.class));
        assertTrue(PipelineModelBuilder.hasTransformAnnotation(BareXmlIngestFunction.class));
        assertTrue(PipelineModelBuilder.hasTransformAnnotation(JsonProjectionFunction.class));
    }

    @Test
    void unannotatedFunctionsFallBackToTheLegacyMapPath() {
        // @Enrich carries no serialisation annotation, and a plain function carries none at all; both fall
        // back to the deprecated config-map path, which is the only path that still emits the block.
        assertFalse(PipelineModelBuilder.hasTransformAnnotation(EnrichFunction.class));
        assertFalse(PipelineModelBuilder.hasTransformAnnotation(UnannotatedFunction.class));
    }

    @Test
    void ingestSchemaProducesInputSerialisationWithConfigPath() {
        Optional<PipelineModel.Serialisation> input =
                PipelineModelBuilder.inputSerialisationFromAnnotation(XmlSchemaIngestFunction.class);

        assertTrue(input.isPresent());
        assertEquals(PipelineModel.Serialisation.Format.XML, input.get().getFormat());
        assertEquals("xml-config/fixml-xml-config.json", input.get().getConfigPath());

        // An ingest carries no output serialisation.
        assertFalse(PipelineModelBuilder.outputSerialisationFromAnnotation(XmlSchemaIngestFunction.class).isPresent());
    }

    @Test
    void bareXmlIngestProducesXmlFormatWithoutConfigPath() {
        Optional<PipelineModel.Serialisation> input =
                PipelineModelBuilder.inputSerialisationFromAnnotation(BareXmlIngestFunction.class);

        assertTrue(input.isPresent());
        assertEquals(PipelineModel.Serialisation.Format.XML, input.get().getFormat());
        assertNull(input.get().getConfigPath());
    }

    @Test
    void projectionProducesOutputSerialisation() {
        Optional<PipelineModel.Serialisation> output =
                PipelineModelBuilder.outputSerialisationFromAnnotation(JsonProjectionFunction.class);

        assertTrue(output.isPresent());
        assertEquals(PipelineModel.Serialisation.Format.JSON, output.get().getFormat());
        assertNull(output.get().getConfigPath());

        // A projection carries no input serialisation.
        assertFalse(PipelineModelBuilder.inputSerialisationFromAnnotation(JsonProjectionFunction.class).isPresent());
    }

    @Test
    void enrichHasNoSerialisation() {
        assertFalse(PipelineModelBuilder.inputSerialisationFromAnnotation(EnrichFunction.class).isPresent());
        assertFalse(PipelineModelBuilder.outputSerialisationFromAnnotation(EnrichFunction.class).isPresent());
    }

    @Test
    void allDslFormatsMapToPipelineFormats() {
        for (SerializationFormat format : SerializationFormat.values()) {
            PipelineModel.Serialisation serialisation = PipelineModelBuilder.toSerialisation(format, "");
            assertEquals(format.name(), serialisation.getFormat().name());
            assertNull(serialisation.getConfigPath());
        }
    }
}
