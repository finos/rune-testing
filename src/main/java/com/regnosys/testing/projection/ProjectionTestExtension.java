package com.regnosys.testing.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.regnosys.rosetta.common.reports.ReportField;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapperCreator;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.FieldValueFlattener;
import com.regnosys.testing.TestingExpectationUtil;
import com.regnosys.testing.reports.ExpectedAndActual;
import com.regnosys.testing.transform.TestPackAndDataSetName;
import com.regnosys.testing.transform.TransformTestResult;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.reports.Tabulator;
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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.regnosys.testing.TestingExpectationUtil.getJsonExpectedAndActual;
import static com.regnosys.testing.TestingExpectationUtil.readStringFromResources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProjectionTestExtension<IN extends RosettaModelObject, OUT extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectionTestExtension.class);
    private ObjectWriter rosettaXMLObjectWriter;
    private Validator xsdValidator;
    private final Module runtimeModule;
    private final Class<IN> inputType;
    private Multimap<TestPackAndDataSetName, TransformTestResult> actualExpectation;
    private Path rootExpectationsPath;
    private Path outputPath;
    private String regBody;

    @Inject
    private RosettaTypeValidator typeValidator;

    public ProjectionTestExtension(Module runtimeModule, Class<IN> inputType, URL xsdSchema, URL xmlConfig) {
        this.runtimeModule = runtimeModule;
        this.inputType = inputType;
        try {
            this.rosettaXMLObjectWriter = RosettaObjectMapperCreator.forXML(xmlConfig.openStream()).create().writerWithDefaultPrettyPrinter();
            SchemaFactory schemaFactory = SchemaFactory
                    .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // required to process xml elements with an maxOccurs greater than 5000 (rather than unbounded)
            schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            Schema schema = schemaFactory.newSchema(xsdSchema);
            this.xsdValidator = schema.newValidator();
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }


    public ProjectionTestExtension(Module runtimeModule, Class<IN> inputType) {
        this.runtimeModule = runtimeModule;
        this.inputType = inputType;
    }

    public ProjectionTestExtension<IN, OUT> withRootExpectationsPath(Path rootExpectationsPath) {
        this.rootExpectationsPath = rootExpectationsPath;
        return this;
    }

    public ProjectionTestExtension<IN, OUT> withOutputPath(Path outputPath) {
        this.outputPath = outputPath;
        return this;
    }

    public ProjectionTestExtension<IN,OUT> withRegBody(String regBody) {
        this.regBody = regBody;
        return this;
    }

    public ProjectionTestExtension<IN, OUT> withWriterAndValidator(URL xmlConfig, URL xsdSchema) {
        try {
            this.rosettaXMLObjectWriter = RosettaObjectMapperCreator.forXML(xmlConfig.openStream()).create().writerWithDefaultPrettyPrinter();
            SchemaFactory schemaFactory = SchemaFactory
                    .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // required to process xml elements with an maxOccurs greater than 5000 (rather than unbounded)
            schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            Schema schema = schemaFactory.newSchema(xsdSchema);
            this.xsdValidator = schema.newValidator();
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        Guice.createInjector(runtimeModule).injectMembers(this);
        actualExpectation = ArrayListMultimap.create();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ProjectionExpectationUtil.writeExpectations(actualExpectation);
    }

    public Stream<Arguments> getArguments(){
        List<URL> testPacksURLs = TestingExpectationUtil.readTestPacksFromPath(rootExpectationsPath, ProjectionTestExtension.class.getClassLoader(), regBody);
        URL pipelineUrl = TestingExpectationUtil.readPipelineFromPath(rootExpectationsPath, ProjectionTestExtension.class.getClassLoader(), regBody);
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();

        PipelineModel pipelineModel = TestingExpectationUtil.readFile(pipelineUrl,mapper, PipelineModel.class);
        return testPacksURLs.stream().flatMap(testPacksUrl -> {
            TestPackModel testPackModel = TestingExpectationUtil.readFile(testPacksUrl, mapper, TestPackModel.class);
            return testPackModel.getSamples().stream().map(sampleModel -> {

                String inputFile = sampleModel.getInputPath();
                URL inputFileUrl = getInputFileUrl(inputFile);
                // input files can be missing if the upstream report has thrown an exception
                if (inputFileUrl == null) {
                    return null;
                }

                IN input = TestingExpectationUtil.readFile(inputFileUrl, mapper, inputType);

                return Arguments.of(
                        pipelineModel,
                        testPackModel.getId(),
                        testPackModel.getPipelineId(),
                        testPackModel.getName(),
                        input,
                        sampleModel);
            });
        });
    }

    private static URL getInputFileUrl(String inputFile) {
        try {
            return Resources.getResource(inputFile);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Failed to load input file " + inputFile);
            return null;
        }
    }

    private Path generateRelativeExpectationFilePath(Path outputPath, URL expectationUrl) {
        try {
            Path path = Path.of(expectationUrl.toURI());
            String relativePath = path.toString().replaceAll("^.*?(\\Q" + outputPath + "\\E.*)", "$1");
            return Path.of(relativePath);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


    public void runProjectionAndAssert(String testPackId,
                                         String pipelineId,
                                         String dataSetName,
                                         TestPackModel.SampleModel sampleModel,
                                         Function<IN, OUT> functionExecutionCallback,
                                         IN input, Tabulator<OUT> tabulator) throws IOException {


        TransformTestResult result = runProjection(sampleModel, functionExecutionCallback, input, tabulator);

        actualExpectation.put(new TestPackAndDataSetName(testPackId, pipelineId, dataSetName), result);

        ExpectedAndActual<String> outputXml = result.getReport();
        assertEquals(outputXml.getExpected(), outputXml.getActual());

        ExpectedAndActual<String> keyValue = result.getKeyValue();
        TestingExpectationUtil.assertJsonEquals(keyValue.getExpected(), keyValue.getActual());

        ExpectedAndActual<Integer> validationFailures = result.getModelValidationFailures();
        assertEquals(validationFailures.getExpected(), validationFailures.getActual(), "Validation failures");

        ExpectedAndActual<Boolean> validXml = result.getSchemaValidationFailure();
        assertEquals(validXml.getExpected(), validXml.getActual(), "XML validation");

        ExpectedAndActual<Boolean> error = result.getRuntimeError();
        assertEquals(error.getExpected(), error.getActual(), "Error");
    }

    private TransformTestResult runProjection(
                                              TestPackModel.SampleModel sampleModel,
                                              Function<IN, OUT> functionExecutionCallback,
                                              IN input,
                                              Tabulator<OUT> tabulator) throws IOException {
        Path outputPath = Paths.get(sampleModel.getOutputPath());
        Path keyValuePath = Paths.get(sampleModel.getOutputTabulatedPath());
        try {
            OUT projectOutput = functionExecutionCallback.apply(input);

            // XML result
            ExpectedAndActual<String> outputXml = getXmlExpectedAndActual(outputPath, projectOutput);
            assertNotNull(projectOutput);

            // Key/value results
            FieldValueFlattener flattener = new FieldValueFlattener();
            tabulator.tabulate(projectOutput).forEach(
                    field -> field.accept(flattener, List.of())
            );
            List<ReportField> results = flattener.accumulator;
            ExpectedAndActual<String> keyValue = getJsonExpectedAndActual(keyValuePath, results);

            // Validation failures
            ValidationReport validationReport = typeValidator.runProcessStep(projectOutput.getType(), projectOutput);
            validationReport.logReport();
            int actualValidationFailures = validationReport.validationFailures().size();
            ExpectedAndActual<Integer> validationFailures = new ExpectedAndActual<>(Path.of(sampleModel.getInputPath()), sampleModel.getAssertions().getModelValidationFailures(), actualValidationFailures);

            // Assert XML output does not match XSD schema.

            boolean actualValidXml = isValidXml(outputXml.getActual());
            ExpectedAndActual<Boolean> validXml = new ExpectedAndActual<>(Path.of(sampleModel.getInputPath()), sampleModel.getAssertions().isSchemaValidationFailure(), actualValidXml);

            // No exceptions
            ExpectedAndActual<Boolean> error = new ExpectedAndActual<>(Path.of(sampleModel.getInputPath()), sampleModel.getAssertions().isRuntimeError(), false);

            return new TransformTestResult(sampleModel, keyValue, outputXml, validationFailures, validXml, error);
        } catch (Exception e) {
            LOGGER.error("Exception occurred running projection", e);
            ExpectedAndActual<String> keyValue = getJsonExpectedAndActual(keyValuePath, Collections.emptyList());
            ExpectedAndActual<String> outputXml = getXmlExpectedAndActual(outputPath, null);
            ExpectedAndActual<Integer> validationFailures = new ExpectedAndActual<>(Path.of(sampleModel.getInputPath()), sampleModel.getAssertions().getModelValidationFailures(), 0);
            ExpectedAndActual<Boolean> validXml = new ExpectedAndActual<>(Path.of(sampleModel.getInputPath()), sampleModel.getAssertions().isSchemaValidationFailure(), false);
            ExpectedAndActual<Boolean> error = new ExpectedAndActual<>(Path.of(sampleModel.getInputPath()), sampleModel.getAssertions().isRuntimeError(), true);
            return new TransformTestResult(sampleModel, keyValue, outputXml, validationFailures, validXml, error);
        }
    }

    private boolean isValidXml(String actualXml) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(actualXml.getBytes(StandardCharsets.UTF_8))) {
            xsdValidator.validate(new StreamSource(inputStream));
            return true;
        } catch (SAXException e) {
            LOGGER.error("Schema validation failed: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ExpectedAndActual<String> getXmlExpectedAndActual(Path expectationPath, Object xmlResult) throws IOException {
        String actualXML = xmlResult != null ?
                rosettaXMLObjectWriter.writeValueAsString(xmlResult) :
                "";
        String expectedXML = readStringFromResources(expectationPath);
        return new ExpectedAndActual<>(expectationPath, expectedXML, actualXML);
    }
}
