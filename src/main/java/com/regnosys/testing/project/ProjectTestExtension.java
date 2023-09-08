package com.regnosys.testing.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.ExpectationUtil;
import com.regnosys.testing.reports.ExpectedAndActual;
import com.regnosys.testing.reports.ReportTestExtension;
import com.rosetta.model.lib.RosettaModelObject;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProjectTestExtension<IN extends RosettaModelObject, OUT extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback {
    public static final String PROJECT_EXPECTATIONS_FILE_NAME = "project-expectations.json";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectTestExtension.class);
    private final Module runtimeModule;
    private final Class<IN> inputType;

    private Multimap<ProjectNameAndDataSetName, ProjectTestResult> actualExpectation;
    private Path rootExpectationsPath;

    @Inject
    private RosettaTypeValidator typeValidator;

    public ProjectTestExtension(Module runtimeModule, Class<IN> inputType) {
        this.runtimeModule = runtimeModule;
        this.inputType = inputType;
    }

    public ProjectTestExtension<IN, OUT> withRootExpectationsPath(Path rootExpectationsPath) {
        this.rootExpectationsPath = rootExpectationsPath;
        return this;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        Guice.createInjector(runtimeModule).injectMembers(this);
        actualExpectation = ArrayListMultimap.create();
    }

    @Override
    public void afterAll(ExtensionContext context) {
    }

    public Stream<Arguments> getArguments() {
        List<URL> expectationFiles = ExpectationUtil.readExpectationsFromPath(rootExpectationsPath, ReportTestExtension.class.getClassLoader(), PROJECT_EXPECTATIONS_FILE_NAME);
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return expectationFiles.stream()
                .flatMap(expectationUrl -> {
                    Path projectExpectationPath;
                    try {
                        projectExpectationPath = Path.of(expectationUrl.toURI());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    ProjectDataSetExpectation projectExpectation = ExpectationUtil.readFile(expectationUrl, mapper, ProjectDataSetExpectation.class);
                    return projectExpectation.getDataItemExpectations().stream()
                            .map(dataItemExpectation -> {
                                // input file to be tested
                                String inputFile = dataItemExpectation.getInputFile();
                                URL inputFileUrl = Resources.getResource(inputFile);
                                // deserialise into input (e.g. ESMAEMIRMarginReport)
                                IN input = ExpectationUtil.readFile(inputFileUrl, mapper, inputType);
                                String projectName = projectExpectation.getProjectName();
                                return Arguments.of(
                                        projectName,
                                        projectExpectationPath,
                                        projectExpectation.getDataSetName(),
                                        input,
                                        dataItemExpectation
                                );
                            });
                });
    }

    public void runProjectionAndAssert(String projectName, Path projectExpectationPath, String dataSetName, ProjectDataItemExpectation expectation, Function<IN, OUT> functionExecutionCallback, IN input) throws IOException {
        Path outputFile = Paths.get(expectation.getOutputFile());

        OUT projectOutput = functionExecutionCallback.apply(input);
        ExpectedAndActual<String> project = ExpectationUtil.getExpectedAndActual(outputFile, projectOutput);

        assertNotNull(projectOutput);

        //validation failures
        ValidationReport validationReport = typeValidator.runProcessStep(projectOutput.getType(), projectOutput);
        validationReport.logReport();
        int actualValidationFailures = validationReport.validationFailures().size();
        ExpectedAndActual<Integer> validationFailures = new ExpectedAndActual<>(projectExpectationPath, expectation.getValidationFailures(), actualValidationFailures);

        ProjectTestResult projectTestResult = new ProjectTestResult(expectation.getInputFile(), project, validationFailures);

        actualExpectation.put(new ProjectNameAndDataSetName(projectName, dataSetName), projectTestResult);

        ExpectationUtil.assertJsonEquals(project.getExpected(), project.getActual());
        assertEquals(validationFailures.getExpected(), validationFailures.getActual(), "Validation failures");
    }

}
