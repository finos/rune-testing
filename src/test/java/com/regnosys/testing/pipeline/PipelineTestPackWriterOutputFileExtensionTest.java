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

import com.regnosys.rosetta.common.transform.PipelineModel;
import com.rosetta.model.lib.functions.RosettaFunction;
import com.rosetta.model.lib.transform.Ingest;
import com.rosetta.model.lib.transform.Projection;
import com.rosetta.model.lib.transform.SerializationFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineTestPackWriterOutputFileExtensionTest {

    @Projection(format = SerializationFormat.XML)
    private static class XmlProjectionFunction implements RosettaFunction {
    }

    @Projection(format = SerializationFormat.CSV_LABELLED)
    private static class CsvProjectionFunction implements RosettaFunction {
    }

    @Ingest(format = SerializationFormat.XML)
    private static class XmlIngestFunction implements RosettaFunction {
    }

    private static class UnannotatedFunction implements RosettaFunction {
    }

    @Test
    void projectionAnnotationDecidesExtensionWithoutPipelineSerialisation() {
        assertEquals("xml", PipelineTestPackWriter.outputFileExtension(XmlProjectionFunction.class, null));
        assertEquals("csv", PipelineTestPackWriter.outputFileExtension(CsvProjectionFunction.class, null));
    }

    @Test
    void projectionAnnotationWinsOverPipelineSerialisation() {
        PipelineModel.Serialisation json = new PipelineModel.Serialisation(PipelineModel.Serialisation.Format.JSON, null);
        assertEquals("xml", PipelineTestPackWriter.outputFileExtension(XmlProjectionFunction.class, json));
    }

    @Test
    void unannotatedFunctionFallsBackToPipelineSerialisation() {
        PipelineModel.Serialisation xml = new PipelineModel.Serialisation(PipelineModel.Serialisation.Format.XML, null);
        assertEquals("xml", PipelineTestPackWriter.outputFileExtension(UnannotatedFunction.class, xml));
    }

    @Test
    void ingestOutputDefaultsToJson() {
        assertEquals("json", PipelineTestPackWriter.outputFileExtension(XmlIngestFunction.class, null));
        assertEquals("json", PipelineTestPackWriter.outputFileExtension(UnannotatedFunction.class, null));
    }
}
