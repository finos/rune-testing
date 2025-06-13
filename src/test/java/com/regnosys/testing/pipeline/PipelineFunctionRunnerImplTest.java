package com.regnosys.testing.pipeline;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.io.Resources;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.util.PathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import com.rosetta.model.lib.meta.RosettaMetaData;
import com.rosetta.model.lib.path.RosettaPath;
import com.rosetta.model.lib.process.BuilderMerger;
import com.rosetta.model.lib.process.BuilderProcessor;
import com.rosetta.model.lib.process.PostProcessor;
import com.regnosys.rosetta.common.postprocess.PathCountProcessor;
import com.rosetta.model.lib.process.Processor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static com.regnosys.rosetta.common.transform.TestPackUtils.readFile;
import static com.regnosys.testing.transform.TransformTestExtension.EMPTY_OUTPUT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PipelineFunctionRunnerImplTest {

    private static final ObjectMapper OBJECT_MAPPER = RosettaObjectMapper.getNewMinimalRosettaObjectMapper();
    private static final String EXPECTED_OUTPUT = """
            {
              "test" : "input"
            }""";
    
    @TempDir
    Path tempDir;

    @Mock
    private Function<TestObject, RosettaModelObject> function;

    @Mock
    private RosettaTypeValidator typeValidator;

    @Mock
    private ReferenceConfig referenceConfig;

    @Mock
    private PostProcessor postProcessor;

    @Mock
    private Validator xsdValidator;

    @Mock
    private ValidationReport validationReport;

    private PipelineFunctionRunnerImpl<TestObject> pipelineFunctionRunner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ObjectWriter objectWriter = OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
        pipelineFunctionRunner = new PipelineFunctionRunnerImpl<>(
                TransformType.TRANSLATE,
                function,
                TestObject.class,
                typeValidator,
                referenceConfig,
                OBJECT_MAPPER,
                objectWriter,
                postProcessor,
                xsdValidator
        );
    }

    @Test
    void testRunWithValidInput() throws IOException, SAXException {
        // Create a test input file
        URL resource = Resources.getResource("pipeline-test/test.json");
        Path inputPath = UrlUtils.toPath(resource);

        TestObject testOutput = readFile(resource, OBJECT_MAPPER, TestObject.class);

        // Mock the function to return a test output
        when(function.apply(any(TestObject.class))).thenReturn(testOutput);

        // Mock the output object writer to return a serialized output

        // Mock the validation report
        when(typeValidator.runProcessStep(any(), any())).thenReturn(validationReport);
        when(validationReport.validationFailures()).thenReturn(java.util.Collections.emptyList());

        // Mock the schema validation
        doNothing().when(xsdValidator).validate(any(StreamSource.class));

        // Run the function
        PipelineFunctionResult result = pipelineFunctionRunner.run(inputPath);

        // Verify the result
        assertNotNull(result);
        assertEquals(EXPECTED_OUTPUT, result.getSerialisedOutput());
        assertEquals(validationReport, result.getValidationReport());

        TestPackModel.SampleModel.Assertions assertions = result.getAssertions();
        assertNotNull(assertions);
        assertEquals(1, assertions.getInputPathCount());
        assertEquals(1, assertions.getOutputPathCount());
        assertEquals(0, assertions.getModelValidationFailures());
        assertTrue(assertions.isSchemaValidationFailure());
        assertFalse(assertions.isRuntimeError());

        // Verify the interactions
        verify(function).apply(any(TestObject.class));
        verify(typeValidator).runProcessStep(any(), any());
        verify(validationReport).validationFailures();
        verify(xsdValidator).validate(any(StreamSource.class));
    }

    @Test
    void testRunWithNullInput() {
        // Create a test input file
        URL resource = Resources.getResource("pipeline-test/empty.json");
        Path inputPath = UrlUtils.toPath(resource);

        // Run the function
        PipelineFunctionResult result = pipelineFunctionRunner.run(inputPath);

        // Verify the result
        assertNotNull(result);
        assertEquals(EMPTY_OUTPUT, result.getSerialisedOutput());
        assertNull(result.getValidationReport());

        TestPackModel.SampleModel.Assertions assertions = result.getAssertions();
        assertNotNull(assertions);
        assertNull(assertions.getInputPathCount());
        assertNull(assertions.getOutputPathCount());
        assertNull(assertions.getModelValidationFailures());
        assertNull(assertions.isSchemaValidationFailure());
        assertFalse(assertions.isRuntimeError());

        // Verify the interactions
        verify(function, never()).apply(any(TestObject.class));
    }

    @Test
    void testRunWithNullOutput() {
        // Create a test input file
        URL resource = Resources.getResource("pipeline-test/test.json");
        Path inputPath = UrlUtils.toPath(resource);

        // Mock the function to return null
        when(function.apply(any(TestObject.class))).thenReturn(null);

        // Run the function
        PipelineFunctionResult result = pipelineFunctionRunner.run(inputPath);

        // Verify the result
        assertNotNull(result);
        assertEquals(EMPTY_OUTPUT, result.getSerialisedOutput());
        assertNull(result.getValidationReport());

        TestPackModel.SampleModel.Assertions assertions = result.getAssertions();
        assertNotNull(assertions);
        assertEquals(1, assertions.getInputPathCount());
        assertNull(assertions.getOutputPathCount());
        assertNull(assertions.getModelValidationFailures());
        assertNull(assertions.isSchemaValidationFailure());
        assertFalse(assertions.isRuntimeError());

        // Verify the interactions
        verify(function).apply(any(TestObject.class));
    }

    @Test
    void testRunWithException() {
        // Create a test input file
        URL resource = Resources.getResource("pipeline-test/test.json");
        Path inputPath = UrlUtils.toPath(resource);

        // Mock the input object mapper to throw an exception
        when(function.apply(any(TestObject.class)))
                .thenThrow(new RuntimeException("Test exception"));
        
        // Run the function
        PipelineFunctionResult result = pipelineFunctionRunner.run(inputPath);

        // Verify the result
        assertNotNull(result);
        assertEquals(EMPTY_OUTPUT, result.getSerialisedOutput());
        assertNull(result.getValidationReport());

        TestPackModel.SampleModel.Assertions assertions = result.getAssertions();
        assertNotNull(assertions);
        assertEquals(1, assertions.getInputPathCount());
        assertNull(assertions.getOutputPathCount());
        assertNull(assertions.getModelValidationFailures());
        assertNull(assertions.isSchemaValidationFailure());
        assertTrue(assertions.isRuntimeError());
    }
}
