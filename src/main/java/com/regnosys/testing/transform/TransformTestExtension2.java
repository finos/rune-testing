package com.regnosys.testing.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.TestingExpectationUtil;
import com.regnosys.testing.reports.ReportExpectationUtil;
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

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.testing.TestingExpectationUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TransformTestExtension2<T> implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformTestExtension2.class);
    private final Module runtimeModule;
    private final Path resourcePath;
    private final Class<T> funcType;
    @Inject
    private RosettaTypeValidator typeValidator;
    @Inject
    private ReferenceConfig referenceConfig;
    private Multimap<String, TransformTestResult> actualExpectation;
    private PipelineModel pipelineModel;
    private Injector injector;


    public TransformTestExtension2(TransformType transformType, Module runtimeModule, Class<T> funcType) {
        this.runtimeModule = runtimeModule;
        this.resourcePath = Path.of(transformType.getResourcePath());
        this.funcType = funcType;
    }

    @BeforeAll
    public void beforeAll(ExtensionContext context) {
        injector = Guice.createInjector(runtimeModule);
        injector.injectMembers(this);
        ClassLoader classLoader = this.getClass().getClassLoader();
        pipelineModel = getPipelineModel(funcType.getName(), classLoader, resourcePath);
        actualExpectation = ArrayListMultimap.create();
    }

    @AfterAll
    public void afterAll(ExtensionContext context) throws Exception {
        writeExpectations(actualExpectation);
    }
    
    public <IN extends RosettaModelObject, OUT extends RosettaModelObject> void runTransformAndAssert(
            String testPackId, TestPackModel.SampleModel sampleModel, Function<IN, OUT> transformFunc) {

        TransformTestResult result = getResult(sampleModel, transformFunc);

        actualExpectation.put(testPackId, result);

        Path outputPath = Path.of(sampleModel.getOutputPath());
        String expectedOutput = readStringFromResources(outputPath);
        String actualOutput = result.getOutput();
        assertEquals(expectedOutput, actualOutput);

        TestPackModel.SampleModel.Assertions actualAssertions = result.getSampleModel().getAssertions();
        TestPackModel.SampleModel.Assertions expectedAssertions = sampleModel.getAssertions();
        assertEquals(expectedAssertions, actualAssertions);
    }

    private <IN extends RosettaModelObject, OUT extends RosettaModelObject> TransformTestResult getResult(TestPackModel.SampleModel sampleModel, Function<IN, OUT> function) {
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();

        String inputFile = sampleModel.getInputPath();
        URL inputFileUrl = getInputFileUrl(inputFile);
        Class<IN> inputType = getInputType();
        IN input = TestingExpectationUtil.readFile(inputFileUrl, mapper, inputType);

        try {
            // report
            IN resolvedInput = resolveReferences(input);
            OUT output = function.apply(resolvedInput);

            assertNotNull(output);

            // validation failures
            ValidationReport validationReport = typeValidator.runProcessStep(output.getType(), output);
            validationReport.logReport();
            int actualValidationFailures = validationReport.validationFailures().size();

            TestPackModel.SampleModel.Assertions assertions = new TestPackModel.SampleModel.Assertions(actualValidationFailures, null, false);
            return new TransformTestResult(ROSETTA_OBJECT_WRITER.writeValueAsString(output), updateSampleModel(sampleModel, assertions));

        } catch (Exception e) {
            LOGGER.error("Exception occurred running projection", e);
            TestPackModel.SampleModel.Assertions assertions = new TestPackModel.SampleModel.Assertions(null, null, true);
            return new TransformTestResult(null, updateSampleModel(sampleModel, assertions));
        }
    }

    private TestPackModel.SampleModel updateSampleModel(TestPackModel.SampleModel sampleModel, TestPackModel.SampleModel.Assertions assertions) {
        return new TestPackModel.SampleModel(sampleModel.getId(), sampleModel.getName(), sampleModel.getInputPath(), sampleModel.getOutputPath(), sampleModel.getOutputTabulatedPath(), assertions);
    }

    public Stream<Arguments> getArguments() {
        T func = injector.getInstance(funcType);
        ClassLoader classLoader = this.getClass().getClassLoader();
        List<TestPackModel> testPackModels = getTestPackModels(pipelineModel.getId(), classLoader, resourcePath);
        return testPackModels.stream()
                .flatMap(testPackModel -> testPackModel.getSamples().stream()
                        .map(sampleModel ->
                            Arguments.of(
                                    String.format("%s | %s", testPackModel.getName(), sampleModel.getId()),
                                    testPackModel.getId(),
                                    sampleModel,
                                    func)))
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

    private <IN extends RosettaModelObject> Class<IN> getInputType() {
        try {
            return (Class<IN>) Class.forName(pipelineModel.getTransform().getInputType());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private <T extends RosettaModelObject> T resolveReferences(T modelObject) {
        RosettaModelObjectBuilder builder = modelObject.toBuilder();
        new ReferenceResolverProcessStep(referenceConfig).runProcessStep(modelObject.getType(), builder);
        return (T) builder.build();
    }

    protected void writeExpectations(Multimap<String, TransformTestResult> actualExpectation) throws Exception {
        ReportExpectationUtil.writeExpectations(actualExpectation, resourcePath);
    }

    // TODO move to util class?
    private static PipelineModel getPipelineModel(String functionName, ClassLoader classLoader, Path resourcePath) {
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        List<URL> pipelineFiles = TestingExpectationUtil.readExpectationsFromPath(resourcePath, classLoader, "pipeline-.*\\.json");
        return pipelineFiles.stream()
                .map(url -> TestingExpectationUtil.readFile(url, mapper, PipelineModel.class))
                .filter(p -> p.getTransform().getFunction().equals(functionName))
                .findFirst()
                .orElseThrow();
    }

    // TODO move to util class?
    private static List<TestPackModel> getTestPackModels(String pipelineId, ClassLoader classLoader, Path resourcePath) {
        List<URL> testPackUrls = TestingExpectationUtil.readExpectationsFromPath(resourcePath, classLoader, "test-pack-.*\\.json");
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return testPackUrls.stream()
                .map(url -> TestingExpectationUtil.readFile(url, mapper, TestPackModel.class))
                .filter(testPackModel -> testPackModel.getPipelineId() != null)
                .filter(testPackModel -> testPackModel.getPipelineId().equals(pipelineId))
                .collect(Collectors.toList());
    }
}
