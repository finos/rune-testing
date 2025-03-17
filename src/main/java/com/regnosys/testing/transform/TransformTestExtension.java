package com.regnosys.testing.transform;

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

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.hashing.ReferenceResolverProcessStep;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TestPackUtils;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.TestingExpectationUtil;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.rosetta.common.transform.TestPackUtils.*;
import static com.regnosys.testing.TestingExpectationUtil.readStringFromResources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TransformTestExtension<T> implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformTestExtension.class);

    private static final ObjectMapper JSON_OBJECT_MAPPER = RosettaObjectMapper.getNewRosettaObjectMapper();

    private ObjectWriter jsonObjectWriter =
            JSON_OBJECT_MAPPER
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .writerWithDefaultPrettyPrinter();
    // use empty string as error value for function output as it gets serialised
    public static final String ERROR_OUTPUT = "";

    private String pipelineId;
    private final Module runtimeModule;
    private final Path configPath;
    private final Class<T> funcType;
    private Validator xsdValidator;
    @Inject
    RosettaTypeValidator typeValidator;
    @Inject
    ReferenceConfig referenceConfig;
    private Multimap<String, TransformTestResult> actualExpectation;
    private List<PipelineModel> pipelineModel;
    private Injector injector;
    private ObjectMapper inputObjectMapper;
    private ObjectWriter outputObjectWriter;

    public TransformTestExtension(Module runtimeModule, Path configPath, Class<T> funcType) {
        this.runtimeModule = runtimeModule;
        this.configPath = configPath;
        this.funcType = funcType;
    }

    public TransformTestExtension(String pipelineId, Module runtimeModule, Path configPath, Class<T> funcType) {
        this.pipelineId = pipelineId;
        this.runtimeModule = runtimeModule;
        this.configPath = configPath;
        this.funcType = funcType;
    }

    public TransformTestExtension<T> withSortJsonPropertiesAlphabetically(boolean sortJsonPropertiesAlphabetically) {
        jsonObjectWriter =
                JSON_OBJECT_MAPPER
                        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, sortJsonPropertiesAlphabetically)
                        .writerWithDefaultPrettyPrinter();
        return this;
    }
    
    public TransformTestExtension<T> withSchemaValidation(URL xsdSchema) {
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // required to process xml elements with an maxOccurs greater than 5000 (rather than unbounded)
            schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            Schema schema = schemaFactory.newSchema(xsdSchema);
            this.xsdValidator = schema.newValidator();
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @BeforeAll
    public void beforeAll(ExtensionContext context) {
        this.injector = Guice.createInjector(runtimeModule);
        this.injector.injectMembers(this);
        ClassLoader classLoader = this.getClass().getClassLoader();
        List<PipelineModel> pipelineModels = getPipelineModels(configPath, classLoader, JSON_OBJECT_MAPPER);
        List<PipelineModel> populatedModels = new ArrayList<>();
        for (PipelineModel model : pipelineModels) {
            populatedModels.addAll(getPipelineModel(model.getId(), pipelineModels, funcType.getName()));

            if (!populatedModels.isEmpty()) {
                if (this.pipelineModel == null || this.pipelineModel.isEmpty()) {
                    this.pipelineModel = populatedModels;
                    this.inputObjectMapper = getObjectMapper(model.getInputSerialisation()).orElse(JSON_OBJECT_MAPPER);
                    this.outputObjectWriter = getObjectWriter(model.getOutputSerialisation()).orElse(jsonObjectWriter);
                }
            }
        }

        if (this.pipelineModel == null || this.pipelineModel.isEmpty()) {
            throw new IllegalArgumentException("No Matching PipelineModels found");
        }

        //Finally Filter out any pipeline models that are not the one we are interested in if we restrict the test extension id:
        if (this.pipelineId != null) {
            this.pipelineModel = this.pipelineModel.stream()
                    .filter(p -> p.getId().equals(this.pipelineId)).collect(Collectors.toList());
        }
        this.actualExpectation = ArrayListMultimap.create();
    }

    @AfterAll
    public void afterAll(ExtensionContext context) throws Exception {
        writeExpectations(actualExpectation);
    }

    public <IN extends RosettaModelObject, OUT extends RosettaModelObject> void runTransformAndAssert(
            String testPackId, TestPackModel.SampleModel sampleModel, Function<IN, OUT> transformFunc) {

        TransformTestResult result = getResult(sampleModel, transformFunc);

        if (TestingExpectationUtil.WRITE_EXPECTATIONS) {
            actualExpectation.put(testPackId, result);
        }

        String actualOutput = result.getOutput();
        Path outputPath = Path.of(sampleModel.getOutputPath());
        String expectedOutput = readStringFromResources(outputPath);
        assertEquals(expectedOutput, actualOutput);

        TestPackModel.SampleModel.Assertions actualAssertions = result.getSampleModel().getAssertions();
        TestPackModel.SampleModel.Assertions expectedAssertions = sampleModel.getAssertions();
        assertEquals(expectedAssertions, actualAssertions);
    }

    protected <IN extends RosettaModelObject, OUT extends RosettaModelObject> TransformTestResult getResult(TestPackModel.SampleModel sampleModel, Function<IN, OUT> function) {
        String inputFile = sampleModel.getInputPath();
        URL inputFileUrl = getInputFileUrl(inputFile);
        assertNotNull(inputFileUrl);

        Class<IN> inputType = getInputType();

        try {
            IN input = readFile(inputFileUrl, inputObjectMapper, inputType);
            IN resolvedInput = resolveReferences(input);
            OUT output = function.apply(resolvedInput);

            assertNotNull(output);

            // serialised output
            String serialisedOutput = outputObjectWriter.writeValueAsString(output);

            // validation failures
            ValidationReport validationReport = typeValidator.runProcessStep(output.getType(), output);
            validationReport.logReport();
            int actualValidationFailures = validationReport.validationFailures().size();

            // schema validation
            Boolean schemaValidationFailure = isSchemaValidationFailure(serialisedOutput);

            TestPackModel.SampleModel.Assertions assertions =
                    new TestPackModel.SampleModel.Assertions(actualValidationFailures, schemaValidationFailure, false);
            return new TransformTestResult(serialisedOutput, updateSampleModel(sampleModel, assertions));
        } catch (Exception e) {
            LOGGER.error("Exception occurred running transform", e);
            TestPackModel.SampleModel.Assertions assertions = new TestPackModel.SampleModel.Assertions(null, null, true);
            return new TransformTestResult(ERROR_OUTPUT, updateSampleModel(sampleModel, assertions));
        }
    }

    public Stream<Arguments> getArguments() {
        T func = injector.getInstance(funcType);
        ClassLoader classLoader = this.getClass().getClassLoader();

        return pipelineModel.stream()
                .flatMap(model -> {
                    List<TestPackModel> testPackModels = getTestPackModels(
                            TestPackUtils.getTestPackModels(configPath, classLoader, JSON_OBJECT_MAPPER),
                            model.getId()
                    );
                    return testPackModels.stream()
                            .flatMap(testPackModel -> testPackModel.getSamples().stream()
                                    .map(sampleModel -> Arguments.of(
                                            String.format("%s | %s", testPackModel.getName(), sampleModel.getId()),
                                            testPackModel.getId(),
                                            sampleModel,
                                            func
                                    ))
                            );
                })
                .filter(Objects::nonNull);
    }

    private static URL getInputFileUrl(String inputFile) {
        try {
            return Resources.getResource(inputFile);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Failed to load input file " + inputFile);
            return null;
        }
    }

    protected <IN extends RosettaModelObject> Class<IN> getInputType() {
        for (PipelineModel model : pipelineModel) {
            try {
                return (Class<IN>) Class.forName(model.getTransform().getInputType());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("No valid input type found in pipeline models");
    }

    protected <T extends RosettaModelObject> T resolveReferences(T modelObject) {
        RosettaModelObjectBuilder builder = modelObject.toBuilder();
        new ReferenceResolverProcessStep(referenceConfig).runProcessStep(modelObject.getType(), builder);
        return (T) builder.build();
    }

    protected void writeExpectations(Multimap<String, TransformTestResult> actualExpectation) throws Exception {
        TransformExpectationUtil.writeExpectations(actualExpectation, configPath);
    }

    protected Boolean isSchemaValidationFailure(String actualXml) {
        if (xsdValidator == null) {
            return null;
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(actualXml.getBytes(StandardCharsets.UTF_8))) {
            xsdValidator.validate(new StreamSource(inputStream));
            return true;
        } catch (SAXException e) {
            LOGGER.error("Schema validation failed: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected TestPackModel.SampleModel updateSampleModel(TestPackModel.SampleModel sampleModel, TestPackModel.SampleModel.Assertions assertions) {
        return new TestPackModel.SampleModel(sampleModel.getId(), sampleModel.getName(), sampleModel.getInputPath(), sampleModel.getOutputPath(), assertions);
    }
}
