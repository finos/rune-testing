package com.regnosys.testing.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.testing.ExpectationUtil;
import com.regnosys.testing.reports.ReportTestExtension;
import com.rosetta.model.lib.RosettaModelObject;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.regnosys.rosetta.common.reports.RegReportPaths.REPORT_EXPECTATIONS_FILE_NAME;

public class ProjectTestExtension<IN extends RosettaModelObject, OUT extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectTestExtension.class);
    private final Module runtimeModule;
    private final Class<IN> inputType;

    private Multimap<ReportNameAndDataSetName, ProjectTestResult> actualExpectation;
    private Path rootExpectationsPath;

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
        List<URL> expectationFiles = ExpectationUtil.readExpectationsFromPath(rootExpectationsPath, ReportTestExtension.class.getClassLoader(), REPORT_EXPECTATIONS_FILE_NAME);
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return expectationFiles.stream()
                .map(expectationUrl -> ExpectationUtil.readFile(expectationUrl, mapper, ProjectDataSetExpectation.class))
                .flatMap(projectExpectation ->
                        projectExpectation.getDataItemExpectations().stream()
                                .map(dataItemExpectation -> {
                                    // input file to be tested
                                    String inputFile = dataItemExpectation.getInputFile();
                                    URL inputFileUrl = Resources.getResource(inputFile);
                                    // deserialise into input (e.g. ESMAEMIRMarginReport)
                                    IN input = ExpectationUtil.readFile(inputFileUrl, mapper, inputType);
                                    String projectName = projectExpectation.getProjectName();
                                    return Arguments.of(
                                            projectName,
                                            projectExpectation.getDataSetName(),
                                            input,
                                            dataItemExpectation
                                    );
                                }));
    }

    public void runMappingAndAssert(String projectName, String dataSetName, ProjectDataItemExpectation expectation, Function<IN, OUT> functionExecutionCallback, IN input) {

    }
}
