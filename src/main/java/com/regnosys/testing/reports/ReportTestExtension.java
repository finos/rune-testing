package com.regnosys.testing.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.hashing.ReferenceResolverProcessStep;
import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.regnosys.rosetta.common.reports.ReportField;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.FieldValueFlattener;
import com.regnosys.testing.TestingExpectationUtil;
import com.rosetta.model.lib.ModelReportId;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import com.rosetta.model.lib.reports.ReportFunction;
import com.rosetta.model.lib.reports.Tabulator;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.regnosys.rosetta.common.reports.RegReportPaths.REPORT_EXPECTATIONS_FILE_NAME;
import static com.regnosys.testing.TestingExpectationUtil.getExpectedAndActual;
import static com.regnosys.testing.reports.FileNameProcessor.removeFileExtension;
import static com.regnosys.testing.reports.FileNameProcessor.removeFilePrefix;
import static com.regnosys.testing.reports.ReportExpectationUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ReportTestExtension<T extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportTestExtension.class);
    private final Module runtimeModule;
    private final Class<T> inputType;
    private Path rootExpectationsPath;

    @Inject
    private RosettaTypeValidator typeValidator;
    @Inject
    private ReferenceConfig referenceConfig;

    private Multimap<ReportIdentifierAndDataSetName, ReportTestResult> actualExpectation;

    public ReportTestExtension(Module runtimeModule, Class<T> inputType) {
        this.runtimeModule = runtimeModule;
        this.inputType = inputType;
    }

    public ReportTestExtension<T> withRootExpectationsPath(Path rootExpectationsPath) {
        this.rootExpectationsPath = rootExpectationsPath;
        return this;
    }

    @BeforeAll
    public void beforeAll(ExtensionContext context) {
        Guice.createInjector(runtimeModule).injectMembers(this);
        actualExpectation = ArrayListMultimap.create();
    }

    public <In extends RosettaModelObject, Out extends RosettaModelObject> void runReportAndAssertExpected(
            ModelReportId reportIdentifier,
            String dataSetName,
            ReportDataItemExpectation expectation,
            ReportFunction<In, Out> reportFunction,
            Tabulator<Out> tabulator,
            In input) throws IOException {

        Path inputFileName = Paths.get(expectation.getFileName());
        Path outputPath = RegReportPaths.getDefault().getOutputRelativePath();

        // report
        Out reportOutput = reportFunction.evaluate(resolved(input));
        Path reportExpectationPath = RegReportPaths.getReportExpectationFilePath(outputPath, reportIdentifier, dataSetName, inputFileName);
        ExpectedAndActual<String> report = getExpectedAndActual(reportExpectationPath, reportOutput);

        // key value
        FieldValueFlattener flattener = new FieldValueFlattener();
        tabulator.tabulate(reportOutput).forEach(
                field -> field.accept(flattener, List.of())
        );
        List<ReportField> results = flattener.accumulator;
        Path keyValueExpectationPath = RegReportPaths.getKeyValueExpectationFilePath(outputPath, reportIdentifier, dataSetName, inputFileName);
        ExpectedAndActual<String> keyValue = getExpectedAndActual(keyValueExpectationPath, results);

        if (reportOutput == null && report.getExpected() == null) {
            LOGGER.info("Empty report is expected result for {}", expectation.getFileName());
            return;
        }
        assertNotNull(reportOutput);

        // validation failures
        ValidationReport validationReport = typeValidator.runProcessStep(reportOutput.getType(), reportOutput);
        validationReport.logReport();
        int actualValidationFailures = validationReport.validationFailures().size();
        Path reportDataSetExpectationsPath = RegReportPaths.getReportExpectationsFilePath(outputPath, reportIdentifier, dataSetName);
        ExpectedAndActual<Integer> validationFailures = new ExpectedAndActual<>(reportDataSetExpectationsPath, expectation.getValidationFailures(), actualValidationFailures);

        ReportTestResult testExpectation = new ReportTestResult(expectation.getFileName(), keyValue, report, validationFailures);

        actualExpectation.put(new ReportIdentifierAndDataSetName(reportIdentifier, dataSetName), testExpectation);

        TestingExpectationUtil.assertJsonEquals(keyValue.getExpected(), keyValue.getActual());
        TestingExpectationUtil.assertJsonEquals(report.getExpected(), report.getActual());
        assertEquals(validationFailures.getExpected(), validationFailures.getActual(), "Validation failures");
    }
    private <T extends RosettaModelObject> T resolved(T modelObject) {
        RosettaModelObjectBuilder builder = modelObject.toBuilder();
        new ReferenceResolverProcessStep(referenceConfig).runProcessStep(modelObject.getType(), builder);
        return (T) builder.build();
    }

    @AfterAll
    public void afterAll(ExtensionContext context) throws IOException {
        writeExpectations(actualExpectation);
    }

    public Stream<Arguments> getArguments() {
        // find list of expectation files within the report path
        // - warning this will find paths in all classpath jars, so may return additional unwanted paths
        List<URL> expectationFiles = TestingExpectationUtil.readExpectationsFromPath(rootExpectationsPath, ReportTestExtension.class.getClassLoader(), REPORT_EXPECTATIONS_FILE_NAME);
        return getArguments(expectationFiles);
    }

    public Stream<Arguments> getArguments(List<URL> expectationFiles) {
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return expectationFiles.stream()
                // report-data-set expectation contains all expectations for a data-set (e.g. a test pack)
                .map(reportExpectationUrl -> TestingExpectationUtil.readFile(reportExpectationUrl, mapper, ReportDataSetExpectation.class))
                .flatMap(reportExpectation ->
                        // report-data-item expectation contains expectations of a single input file
                        reportExpectation.getDataItemExpectations().stream()
                                .map(dataItemExpectation -> {
                                    // input file to be tested
                                    String fileName = dataItemExpectation.getFileName();
                                    URL inputFileUrl = Resources.getResource(fileName);
                                    // deserialise into input (e.g. ReportableEvent)
                                    T input = TestingExpectationUtil.readFile(inputFileUrl, mapper, inputType);
                                    // get the report identifier
                                    ModelReportId reportIdentifier = reportExpectation.getReportId();
                                    // data item name
                                    String inputName = removeFileExtension(removeFilePrefix(fileName)).replace("-", " ");
                                    return Arguments.of(
                                            String.format("%s | %s", reportIdentifier, inputName),
                                            reportIdentifier,
                                            reportExpectation.getDataSetName(),
                                            input,
                                            dataItemExpectation);
                                }));
    }

    public Module getRuntimeModule() {
        return runtimeModule;
    }
}
