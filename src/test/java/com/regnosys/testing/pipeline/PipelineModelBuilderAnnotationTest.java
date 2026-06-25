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

import com.google.common.collect.ImmutableMap;
import com.regnosys.rosetta.common.transform.FunctionNameHelper;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TransformType;
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
 * The annotation supplies the serialisation only for the direction it governs ({@code @Ingest} the input,
 * {@code @Projection} the output). The opposite direction is still needed for testing but is not expressed
 * by the annotation, so the builder keeps deriving it from the deprecated config maps. These tests cover
 * both the per-direction annotation helpers and the combined {@link PipelineModelBuilder#build} behaviour.
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

    private static class InputThing {
    }

    private static class OutputThing {
    }

    @Ingest(format = SerializationFormat.XML, configPath = "xml-config/in.json")
    private static class IngestFunctionWithBody implements RosettaFunction {
        public OutputThing evaluate(InputThing input) {
            return null;
        }
    }

    @Projection(format = SerializationFormat.JSON)
    private static class ProjectionFunctionWithBody implements RosettaFunction {
        public OutputThing evaluate(InputThing input) {
            return null;
        }
    }

    @Test
    void annotatedFunctionsCarryTransformAnnotation() {
        assertTrue(PipelineModelBuilder.hasTransformAnnotation(XmlSchemaIngestFunction.class));
        assertTrue(PipelineModelBuilder.hasTransformAnnotation(BareXmlIngestFunction.class));
        assertTrue(PipelineModelBuilder.hasTransformAnnotation(JsonProjectionFunction.class));
    }

    @Test
    void ingestOmitsInputSerialisationButKeepsOutputFromPipelineMap() {
        FunctionNameHelper helper = new FunctionNameHelper();
        PipelineModelBuilder builder = new PipelineModelBuilder(helper);
        // The pipeline still configures the output serialisation (the direction @Ingest does not cover).
        PipelineTreeConfig config = new PipelineTreeConfig()
                .withOutputSerialisationFormatMap(ImmutableMap.of(OutputThing.class, PipelineModel.Serialisation.Format.JSON));
        PipelineNode node = new PipelineNode("test", helper, TransformType.TRANSLATE, IngestFunctionWithBody.class, null);

        PipelineModel model = builder.build(node, config);

        // input is omitted - the @Ingest annotation expresses it and the object mapper reads it directly
        assertNull(model.getInputSerialisation());
        // output still comes from the pipeline map
        assertEquals(PipelineModel.Serialisation.Format.JSON, model.getOutputSerialisation().getFormat());
    }

    @Test
    void projectionOmitsOutputSerialisationButKeepsInputFromPipelineMap() {
        FunctionNameHelper helper = new FunctionNameHelper();
        PipelineModelBuilder builder = new PipelineModelBuilder(helper);
        // The pipeline still configures the input serialisation (the direction @Projection does not cover).
        PipelineTreeConfig config = new PipelineTreeConfig()
                .withInputSerialisationFormatMap(ImmutableMap.of(InputThing.class, PipelineModel.Serialisation.Format.XML))
                .withXmlConfigMap(ImmutableMap.of(InputThing.class, "xml-config/proj-in.json"));
        PipelineNode node = new PipelineNode("test", helper, TransformType.PROJECTION, ProjectionFunctionWithBody.class, null);

        PipelineModel model = builder.build(node, config);

        // output is omitted - the @Projection annotation expresses it and the object mapper reads it directly
        assertNull(model.getOutputSerialisation());
        // input still comes from the pipeline map
        assertEquals(PipelineModel.Serialisation.Format.XML, model.getInputSerialisation().getFormat());
        assertEquals("xml-config/proj-in.json", model.getInputSerialisation().getConfigPath());
    }

    @Test
    void unannotatedFunctionsFallBackToTheLegacyMapPath() {
        // @Enrich carries no serialisation annotation, and a plain function carries none at all; both
        // directions then come entirely from the deprecated config-map path.
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
