package com.regnosys.testing.transform;

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
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapperCreator;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TransformType;
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
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.testing.TestingExpectationUtil.ROSETTA_OBJECT_WRITER;
import static com.regnosys.testing.TestingExpectationUtil.readStringFromResources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TransformTestExtension<T> implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformTestExtension.class);
    public static final ObjectMapper OBJECT_MAPPER = RosettaObjectMapper.getNewRosettaObjectMapper();
    private final Module runtimeModule;
    private final Path resourcePath;
    private final Class<T> funcType;
    private Validator xsdValidator;
    @Inject
    private RosettaTypeValidator typeValidator;
    @Inject
    private ReferenceConfig referenceConfig;
    private Multimap<String, TransformTestResult> actualExpectation;
    private PipelineModel pipelineModel;
    private Injector injector;
    private ObjectWriter outputObjectWriter;


    public TransformTestExtension(TransformType transformType, Module runtimeModule, Class<T> funcType) {
        this.runtimeModule = runtimeModule;
        this.resourcePath = Path.of(transformType.getResourcePath());
        this.funcType = funcType;
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
        this.pipelineModel = getPipelineModel(funcType.getName(), classLoader, resourcePath);
        this.outputObjectWriter = getObjectWriter(pipelineModel.getOutputSerialisation());
        this.actualExpectation = ArrayListMultimap.create();
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
        String inputFile = sampleModel.getInputPath();
        URL inputFileUrl = getInputFileUrl(inputFile);
        Class<IN> inputType = getInputType();
        IN input = TestingExpectationUtil.readFile(inputFileUrl, OBJECT_MAPPER, inputType);

        try {
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
        TransformExpectationUtil.writeExpectations(actualExpectation, resourcePath);
    }

    private Boolean isSchemaValidationFailure(String actualXml) {
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

    // TODO move to util class?
    private static PipelineModel getPipelineModel(String functionName, ClassLoader classLoader, Path resourcePath) {
        List<URL> pipelineFiles = TestingExpectationUtil.findPaths(resourcePath, classLoader, "pipeline-.*\\.json");
        return pipelineFiles.stream()
                .map(url -> TestingExpectationUtil.readFile(url, OBJECT_MAPPER, PipelineModel.class))
                .filter(p -> p.getTransform().getFunction().equals(functionName))
                .findFirst()
                .orElseThrow();
    }

    // TODO move to util class?
    private static List<TestPackModel> getTestPackModels(String pipelineId, ClassLoader classLoader, Path resourcePath) {
        List<URL> testPackUrls = TestingExpectationUtil.findPaths(resourcePath, classLoader, "test-pack-.*\\.json");
        return testPackUrls.stream()
                .map(url -> TestingExpectationUtil.readFile(url, OBJECT_MAPPER, TestPackModel.class))
                .filter(testPackModel -> testPackModel.getPipelineId() != null)
                .filter(testPackModel -> testPackModel.getPipelineId().equals(pipelineId))
                .collect(Collectors.toList());
    }

    // TODO move to util class?
    private static ObjectWriter getObjectWriter(PipelineModel.Serialisation outputSerialisation) {
        if (outputSerialisation != null && outputSerialisation.getFormat() == PipelineModel.Serialisation.Format.XML) {
            URL xmlConfigPath = Resources.getResource(outputSerialisation.getConfigPath());
            try {
                return RosettaObjectMapperCreator.forXML(xmlConfigPath.openStream()).create().writerWithDefaultPrettyPrinter();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return ROSETTA_OBJECT_WRITER;
        }
    }
}
