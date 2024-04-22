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
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.FieldValueFlattener;
import com.regnosys.testing.TestingExpectationUtil;
import com.regnosys.testing.reports.ExpectedAndActual;
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
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.regnosys.testing.TestingExpectationUtil.getJsonExpectedAndActual;
import static com.regnosys.testing.TestingExpectationUtil.readStringFromResources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProjectionTestExtension<IN extends RosettaModelObject, OUT extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectionTestExtension.class);
    private final ObjectWriter rosettaXMLObjectWriter;
    private final Validator xsdValidator;
    private final Module runtimeModule;
    private final Class<IN> inputType;
    private Multimap<ProjectionNameAndDataSetName, ProjectionTestResult> actualExpectation;
    private Path rootExpectationsPath;
    private Path outputPath;
    private String testPackFileName;


    @Inject
    private RosettaTypeValidator typeValidator;

    public ProjectionTestExtension(Module runtimeModule, Class<IN> inputType, URL xsdSchema, URL xmlConfig) {
        super ( );
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

    public ProjectionTestExtension<IN, OUT> withRootExpectationsPath(Path rootExpectationsPath) {
        this.rootExpectationsPath = rootExpectationsPath;
        return this;
    }

    public ProjectionTestExtension<IN, OUT> withOutputPath(Path outputPath) {
        this.outputPath = outputPath;
        return this;
    }

    public ProjectionTestExtension<IN, OUT> withTestPackFileName(String testPackFileName) {
        this.testPackFileName = testPackFileName;
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

    public Stream<Arguments> getArguments() {
        List<URL> expectationFiles = TestingExpectationUtil.readExpectationsFromPath(rootExpectationsPath, ProjectionTestExtension.class.getClassLoader(), testPackFileName);
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return expectationFiles.stream()
                .flatMap(expectationUrl -> {
                    Path expectationFilePath = generateRelativeExpectationFilePath(outputPath, expectationUrl);
                    TestPackModel testPackModel = TestingExpectationUtil.readFile(expectationUrl, mapper, TestPackModel.class);
                    return testPackModel.getSamples().stream()
                            .map(sampleModel -> {
                                // input file to be tested
                                String inputFile = sampleModel.getInputPath();
                                URL inputFileUrl = getInputFileUrl(inputFile);
                                // input files can be missing if the upstream report has thrown an exception
                                if (inputFileUrl == null) {
                                    return null;
                                }
                                // deserialise into input (e.g. ESMAEMIRMarginReport)
                                IN input = TestingExpectationUtil.readFile(inputFileUrl, mapper, inputType);
                                String name = testPackModel.getId();

                                return Arguments.of(
                                        String.format("%s | %s", name, Paths.get(inputFile).getFileName()),
                                        name,
                                        expectationFilePath,
                                        testPackModel.getName(),
                                        input,
                                        sampleModel);
                            });
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

    private Path generateRelativeExpectationFilePath(Path outputPath, URL expectationUrl) {
        try {
            Path path = Path.of(expectationUrl.toURI());
            String relativePath = path.toString().replaceAll("^.*?(\\Q" + outputPath + "\\E.*)", "$1");
            return Path.of(relativePath);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void runProjectionAndAssert(String projectName,
                                       Path projectExpectationFilePath,
                                       String dataSetName,
                                       TestPackModel.SampleModel sampleModel,
                                       Function<IN, OUT> functionExecutionCallback,
                                       IN input, Tabulator<OUT> tabulator) throws IOException {


        ProjectionTestResult result = runProjection(projectExpectationFilePath, sampleModel, functionExecutionCallback, input, tabulator);

        actualExpectation.put(new ProjectionNameAndDataSetName(projectName, dataSetName, projectExpectationFilePath), result);

        ExpectedAndActual<String> outputXml = result.getOutput();
        assertEquals(outputXml.getExpected(), outputXml.getActual());

        ExpectedAndActual<String> keyValue = result.getKeyValue();
        TestingExpectationUtil.assertJsonEquals(keyValue.getExpected(), keyValue.getActual());

        ExpectedAndActual<Integer> validationFailures = result.getValidationFailures();
        assertEquals(validationFailures.getExpected(), validationFailures.getActual(), "Validation failures");

        ExpectedAndActual<Boolean> validXml = result.getValidXml();
        assertEquals(validXml.getExpected(), validXml.getActual(), "XML validation");

        ExpectedAndActual<Boolean> error = result.getError();
        assertEquals(error.getExpected(), error.getActual(), "Error");
    }

    private ProjectionTestResult runProjection(Path projectExpectationFilePath,
                                               TestPackModel.SampleModel sampleModel,
                                               Function<IN, OUT> functionExecutionCallback,
                                               IN input,
                                               Tabulator<OUT> tabulator) throws IOException {
        Path outputPath = Paths.get(sampleModel.getOutputPath ());
        Path keyValuePath = Paths.get(sampleModel.getOutputTabulatedPath ());
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
            ExpectedAndActual<Integer> validationFailures = new ExpectedAndActual<>(projectExpectationFilePath, sampleModel.getAssertions().getModelValidationFailures (), actualValidationFailures);

            // Assert XML output does not match XSD schema.
            boolean actualValidXml = isValidXml(outputXml.getActual());
            ExpectedAndActual<Boolean> validXml = new ExpectedAndActual<>(projectExpectationFilePath, sampleModel.getAssertions().isSchemaValidationFailure (), actualValidXml);

            // No exceptions
            ExpectedAndActual<Boolean> error = new ExpectedAndActual<>(projectExpectationFilePath, sampleModel.getAssertions().isRuntimeError (), false);

            return new ProjectionTestResult(sampleModel.getInputPath(), sampleModel.getOutputTabulatedPath(), sampleModel.getOutputPath(), keyValue, outputXml, validationFailures, validXml, error);
        } catch (Exception e) {
            LOGGER.error("Exception occurred running projection", e);
            ExpectedAndActual<String> keyValue = getJsonExpectedAndActual(keyValuePath, Collections.emptyList());
            ExpectedAndActual<String> outputXml = getXmlExpectedAndActual(outputPath, null);
            ExpectedAndActual<Integer> validationFailures = new ExpectedAndActual<>(projectExpectationFilePath, sampleModel.getAssertions().getModelValidationFailures (), 0);
            ExpectedAndActual<Boolean> validXml = new ExpectedAndActual<>(projectExpectationFilePath, sampleModel.getAssertions().isSchemaValidationFailure (), false);
            ExpectedAndActual<Boolean> error = new ExpectedAndActual<>(projectExpectationFilePath, sampleModel.getAssertions().isRuntimeError (), true);
            return new ProjectionTestResult(sampleModel.getInputPath(), sampleModel.getOutputTabulatedPath(), sampleModel.getOutputPath(), keyValue, outputXml, validationFailures, validXml, error);
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
