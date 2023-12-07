package com.regnosys.testing.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapperCreator;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.TestingExpectationUtil;
import com.regnosys.testing.reports.ExpectedAndActual;
import com.rosetta.model.lib.RosettaModelObject;
import org.junit.jupiter.api.Assertions;
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
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.regnosys.testing.TestingExpectationUtil.readStringFromResources;
import static com.regnosys.testing.projection.ProjectionPaths.PROJECTION_EXPECTATIONS_FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProjectionTestExtension<IN extends RosettaModelObject, OUT extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback {
    private final ObjectWriter rosettaXMLObjectWriter;
    private final Validator xsdValidator;

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectionTestExtension.class);
    private final Module runtimeModule;
    private final Class<IN> inputType;

    private Multimap<ProjectionNameAndDataSetName, ProjectionTestResult> actualExpectation;
    private Path rootExpectationsPath;
    private Path outputPath;

    @Inject
    private RosettaTypeValidator typeValidator;

    public ProjectionTestExtension(Module runtimeModule, Class<IN> inputType, URL xsdSchema, URL xmlConfig) {
        this.runtimeModule = runtimeModule;
        this.inputType = inputType;
        try {
            this.rosettaXMLObjectWriter = RosettaObjectMapperCreator.forXML(xmlConfig.openStream()).create().writerWithDefaultPrettyPrinter();
            SchemaFactory schemaFactory = SchemaFactory
                    .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
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

    @Override
    public void beforeAll(ExtensionContext context) {
        Guice.createInjector(runtimeModule).injectMembers(this);
        actualExpectation = ArrayListMultimap.create();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception{
        ProjectionExpectationUtil.writeExpectations(actualExpectation, outputPath);
    }

    public Stream<Arguments> getArguments() {
        List<URL> expectationFiles = TestingExpectationUtil.readExpectationsFromPath(rootExpectationsPath, ProjectionTestExtension.class.getClassLoader(), PROJECTION_EXPECTATIONS_FILE_NAME);
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return expectationFiles.stream()
                .flatMap(expectationUrl -> {
                    Path expectationFilePath = generateRelativeExpectationFilePath(outputPath, expectationUrl);
                    ProjectionDataSetExpectation expectation = TestingExpectationUtil.readFile(expectationUrl, mapper, ProjectionDataSetExpectation.class);
                    return expectation.getDataItemExpectations().stream()
                            .map(dataItemExpectation -> {
                                // input file to be tested
                                String inputFile = dataItemExpectation.getInputFile();
                                URL inputFileUrl = Resources.getResource(inputFile);
                                // deserialise into input (e.g. ESMAEMIRMarginReport)
                                IN input = TestingExpectationUtil.readFile(inputFileUrl, mapper, inputType);
                                String name = expectation.getProjectionName();
                                return Arguments.of(
                                        String.format("%s | %s", expectation.getDataSetName(), Paths.get(inputFile).getFileName()),
                                        name,
                                        expectationFilePath,
                                        expectation.getDataSetName(),
                                        input,
                                        dataItemExpectation
                                );
                            });
                });
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

    public void runProjectionAndAssert(String projectName, Path projectExpectationFilePath, String dataSetName, ProjectionDataItemExpectation expectation, Function<IN, OUT> functionExecutionCallback, IN input) throws IOException {
        Path outputFile = Paths.get(expectation.getOutputFile());

        OUT projectOutput = functionExecutionCallback.apply(input);
        ExpectedAndActual<String> outputXml = getExpectedAndActual(outputFile, projectOutput);

        if (projectOutput == null && outputXml.getExpected() == null) {
            LOGGER.info("Empty project is expected result for {}", expectation.getInputFile());
            return;
        }
        assertNotNull(projectOutput);

        // Validation failures
        ValidationReport validationReport = typeValidator.runProcessStep(projectOutput.getType(), projectOutput);
        validationReport.logReport();
        int actualValidationFailures = validationReport.validationFailures().size();
        ExpectedAndActual<Integer> validationFailures = new ExpectedAndActual<>(projectExpectationFilePath, expectation.getValidationFailures(), actualValidationFailures);

        // Assert XML output does not match XSD schema.
        boolean actualValidXml = isValidXml(outputXml.getActual());
        ExpectedAndActual<Boolean> validXml = new ExpectedAndActual<>(projectExpectationFilePath, expectation.isValidXml(), actualValidXml);

        ProjectionTestResult projectTestResult =
                new ProjectionTestResult(expectation.getInputFile(), expectation.getOutputFile(), outputXml, validationFailures, validXml);

        actualExpectation.put(new ProjectionNameAndDataSetName(projectName, dataSetName, projectExpectationFilePath), projectTestResult);

        Assertions.assertEquals(outputXml.getExpected(), outputXml.getActual());
        assertEquals(validationFailures.getExpected(), validationFailures.getActual(), "Validation failures");
        assertEquals(validXml.getExpected(), validXml.getActual(), "XML validation");
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

    public ExpectedAndActual<String> getExpectedAndActual(Path expectationPath, Object result) throws IOException {
        String actualXML = rosettaXMLObjectWriter.writeValueAsString(result);
        String expectedXML = readStringFromResources(expectationPath);
        return new ExpectedAndActual<>(expectationPath, expectedXML, actualXML);
    }
}
