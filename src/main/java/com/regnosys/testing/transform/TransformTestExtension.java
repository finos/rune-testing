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
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TestPackUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.testing.TestingExpectationUtil;
import com.regnosys.testing.pipeline.PipelineFunctionResult;
import com.regnosys.testing.pipeline.PipelineFunctionRunner;
import com.regnosys.testing.pipeline.PipelineFunctionRunnerProvider;
import com.rosetta.model.lib.RosettaModelObject;
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
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.regnosys.rosetta.common.transform.TestPackUtils.*;
import static com.regnosys.testing.TestingExpectationUtil.readStringFromResources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TransformTestExtension<T> implements BeforeAllCallback, AfterAllCallback {

    // use empty string as error value for function output as it gets serialised
    public static final String ERROR_OUTPUT = "";
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformTestExtension.class);
    private static final ObjectMapper JSON_OBJECT_MAPPER = RosettaObjectMapper.getNewRosettaObjectMapper();
    private final String modelId;
    private final Module runtimeModule;
    private final Path configPath;
    private final Class<T> funcType;
    @Inject
    PipelineFunctionRunnerProvider functionRunnerProvider;
    private ObjectWriter jsonObjectWriter =
            JSON_OBJECT_MAPPER
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .writerWithDefaultPrettyPrinter();
    private Validator outputXsdValidator;
    private PipelineModel pipelineModel;
    private Multimap<String, TransformTestResult> actualExpectation;
    private PipelineFunctionRunner functionRunner;

    public TransformTestExtension(Module runtimeModule, Path configPath, Class<T> funcType) {
        this.runtimeModule = runtimeModule;
        this.configPath = configPath;
        this.funcType = funcType;
        this.modelId = null;
    }

    public TransformTestExtension(String modelId, Module runtimeModule, Path configPath, Class<T> funcType) {
        this.modelId = modelId;
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

    public TransformTestExtension<T> withSchemaValidation(URL outputXsdSchema) {
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // required to process xml elements with an maxOccurs greater than 5000 (rather than unbounded)
            schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            Schema schema = schemaFactory.newSchema(outputXsdSchema);
            this.outputXsdValidator = schema.newValidator();
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
 
    @BeforeAll
    public void beforeAll(ExtensionContext context) {
        Injector injector = Guice.createInjector(runtimeModule);
        injector.injectMembers(this);
        ClassLoader classLoader = this.getClass().getClassLoader();
        this.pipelineModel = getPipelineModel(getPipelineModels(configPath, classLoader, JSON_OBJECT_MAPPER), funcType.getName(), modelId);
        @SuppressWarnings("unchecked")
        Class<? extends RosettaModelObject> inputType = (Class<? extends RosettaModelObject>) toClass(classLoader, pipelineModel.getTransform().getInputType());
        this.functionRunner = functionRunnerProvider.create(
                pipelineModel.getTransform().getType(), 
                inputType,
                funcType, 
                pipelineModel.getInputSerialisation(),
                pipelineModel.getOutputSerialisation(),
                JSON_OBJECT_MAPPER,
                jsonObjectWriter,
                outputXsdValidator);
        this.actualExpectation = ArrayListMultimap.create();
    }

    private Class<?> toClass(ClassLoader classLoader, String type) {
        try {
            return classLoader.loadClass(type);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load class " + type, e);
        }
    }

    @AfterAll
    public void afterAll(ExtensionContext context) throws Exception {
        writeExpectations(actualExpectation);
    }

    public void runTransformAndAssert(String testPackId, TestPackModel.SampleModel sampleModel) {
        URL inputPath = getInputFileUrl(sampleModel.getInputPath());
        assertNotNull(inputPath);

        PipelineFunctionResult result = functionRunner.run(UrlUtils.toPath(inputPath));

        String actualOutput = result.getSerialisedOutput();
        TestPackModel.SampleModel.Assertions actualAssertions = result.getAssertions();

        if (TestingExpectationUtil.WRITE_EXPECTATIONS) {
            actualExpectation.put(testPackId, new TransformTestResult(actualOutput, updateSampleModel(sampleModel, actualAssertions)));
        }

        String expectedOutput = readStringFromResources(Path.of(sampleModel.getOutputPath()));
        assertEquals(expectedOutput, actualOutput);

        TestPackModel.SampleModel.Assertions expectedAssertions = sampleModel.getAssertions();
        assertEquals(expectedAssertions, actualAssertions);
    }

    private URL getInputFileUrl(String inputFile) {
        try {
            return Resources.getResource(inputFile);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Failed to load input file {}", inputFile);
            return null;
        }
    }

    public Stream<Arguments> getArguments() {
        ClassLoader classLoader = this.getClass().getClassLoader();
        List<TestPackModel> testPackModels = getTestPackModels(TestPackUtils.getTestPackModels(configPath, classLoader, JSON_OBJECT_MAPPER), pipelineModel.getId());
        return testPackModels.stream()
                .flatMap(testPackModel -> testPackModel.getSamples().stream()
                        .map(sampleModel ->
                                Arguments.of(
                                        String.format("%s | %s", testPackModel.getName(), sampleModel.getId()),
                                        testPackModel.getId(),
                                        sampleModel)));
    }


    protected void writeExpectations(Multimap<String, TransformTestResult> actualExpectation) throws Exception {
        TransformExpectationUtil.writeExpectations(actualExpectation, configPath);
    }

    private TestPackModel.SampleModel updateSampleModel(TestPackModel.SampleModel sampleModel, TestPackModel.SampleModel.Assertions assertions) {
        return new TestPackModel.SampleModel(sampleModel.getId(), sampleModel.getName(), sampleModel.getInputPath(), sampleModel.getOutputPath(), assertions);
    }
}
