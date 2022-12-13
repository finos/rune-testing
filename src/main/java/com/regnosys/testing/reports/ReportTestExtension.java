package com.regnosys.testing.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.regnosys.rosetta.blueprints.report.RosettaActionFactoryImpl;
import com.regnosys.rosetta.common.reports.RegReport;
import com.regnosys.rosetta.common.reports.RegReportIdentifier;
import com.regnosys.rosetta.common.reports.RegReportUseCase;
import com.regnosys.rosetta.common.reports.ReportField;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.serialisation.reportdata.ReportDataItem;
import com.regnosys.rosetta.common.serialisation.reportdata.ReportDataSet;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.rosetta.reports.api.ReflectiveBlueprintLoader;
import com.regnosys.rosetta.reports.api.RegReportRunner;
import com.regnosys.rosetta.reports.api.ReportExceptionHandler;
import com.rosetta.model.lib.RosettaModelObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.testing.reports.ReportExpectationUtil.*;
import static com.regnosys.testing.reports.ReportUtil.loadRegReportIdentifier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ReportTestExtension<T extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportTestExtension.class);

    private final Module runtimeModule;
    private final ImmutableList<String> rosettaPaths;
    private final Class<T> inputType;
    private final Path rootExpectationsPath;

    private List<RegReportIdentifier> reportIdentifiers;
    private RegReportRunner reportRunner;
    @Inject RosettaTypeValidator typeValidator;

    private Multimap<ReportIdentifierAndDataSet, ReportTestResult> actualExpectation;

    public ReportTestExtension(Module runtimeModule, ImmutableList<String> rosettaPaths, Class<T> inputType, Path rootExpectationsPath) {
        this.runtimeModule = runtimeModule;
        this.rosettaPaths = rosettaPaths;
        this.inputType = inputType;
        this.rootExpectationsPath = rootExpectationsPath;
    }

    @BeforeAll
    public void beforeAll(ExtensionContext context) {
        Guice.createInjector(runtimeModule).injectMembers(this);
        actualExpectation = ArrayListMultimap.create();
        reportIdentifiers = loadRegReportIdentifier(rosettaPaths);

        RosettaActionFactoryImpl actionFactory = new RosettaActionFactoryImpl();
        ReflectiveBlueprintLoader blueprintLoader =
                new ReflectiveBlueprintLoader(ReportTestExtension.class.getClassLoader(), actionFactory);

        reportRunner = new RegReportRunner(
                blueprintLoader,
                Collections.emptyList(),
                Collections.emptyList(),
                reportIdentifiers,
                actionFactory,
                runtimeModule,
                ReportExceptionHandler.noOp());
    }

    public RegReportUseCase runTest(RegReportIdentifier reportIdentifier, String dataSetName, T reportableEvent, ReportDataItemExpectation expectation) {
        LOGGER.info("Running report \"{}\" with data set \"{}\" file \"{}\"", reportIdentifier.getName(), dataSetName, expectation.getName());

        // Create data set with single item
        List<ReportDataItem> reportDataItems = List.of(new ReportDataItem("", reportableEvent, null));
        ReportDataSet reportDataSet = new ReportDataSet(dataSetName, inputType.getSimpleName(), Collections.emptyList(), reportDataItems);

        // Run report
        RegReport result = reportRunner.run(reportIdentifier, List.of(reportDataSet));

        return result.getUseCases().stream().findFirst().orElse(null);
    }

    public void assertTest(RegReportIdentifier reportIdentifier, String dataSetName, ReportDataItemExpectation expectation, RegReportUseCase reportResult) throws IOException {
        assertNotNull(reportResult);

        Path inputPath = Paths.get(expectation.getFileName());

        // key value
        List<ReportField> results = filterEmptyReportFields(reportResult.getResults());
        Path keyValueExpectationPath = getKeyValueExpectationFilePath(reportIdentifier, dataSetName, inputPath);
        ExpectedAndActual<String> keyValue = getExpectedAndActual(keyValueExpectationPath, results);

        // report
        RosettaModelObject useCaseReport = reportResult.getUseCaseReport();
        Path reportExpectationPath = getReportExpectationFilePath(reportIdentifier, dataSetName, inputPath);
        ExpectedAndActual<String> report = getExpectedAndActual(reportExpectationPath, useCaseReport);

        // validation failures
        ValidationReport validationReport = typeValidator.runProcessStep(useCaseReport.getType(), useCaseReport);
        validationReport.logReport();
        int actualValidationFailures = validationReport.validationFailures().size();
        Path reportDataSetExpectationsPath = getReportExpectationsFilePath(reportIdentifier, dataSetName);
        ExpectedAndActual<Integer> validationFailures = new ExpectedAndActual<>(reportDataSetExpectationsPath, expectation.getValidationFailures(), actualValidationFailures);

        ReportTestResult testExpectation = new ReportTestResult(expectation.getName(), expectation.getFileName(), keyValue, report, validationFailures);

        actualExpectation.put(new ReportIdentifierAndDataSet(reportIdentifier, dataSetName), testExpectation);

        assertJsonEquals(keyValue.getExpected(), keyValue.getActual());
        assertJsonEquals(report.getExpected(), report.getActual());
        assertEquals(validationFailures.getExpected(), validationFailures.getActual(), "Validation failures");
    }

    @AfterAll
    public void afterAll(ExtensionContext context) throws IOException {
        writeExpectations(actualExpectation);
    }

    public Stream<Arguments> getArguments() {
        // find list of expectation files within the report path
        List<URL> expectationFiles = readReportExpectationsFromPath(rootExpectationsPath, ReportTestExtension.class.getClassLoader());
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return expectationFiles.stream()
                // report-data-set expectation contains all expectations for a data-set (e.g. a test pack)
                .map(reportExpectationUrl -> readFile(reportExpectationUrl, mapper, ReportDataSetExpectation.class))
                .flatMap(reportExpectation ->
                        // report-data-item expectation contains expectations of a single input file
                        reportExpectation.getDataItemExpectations().stream()
                                .map(dataItemExpectation -> {
                                    // input file to be tested
                                    URL inputFileUrl = Resources.getResource(dataItemExpectation.getFileName());
                                    // deserialise into ReportableEvent
                                    T reportableEvent = readFile(inputFileUrl, mapper, inputType);
                                    // get the report identifier
                                    String reportName = reportExpectation.getReportName();
                                    RegReportIdentifier reportIdentifier = reportIdentifiers.stream()
                                            .filter(r -> r.getName().equals(reportName))
                                            .findFirst().orElseThrow();
                                    return Arguments.of(
                                            String.format("%s | %s", reportName, dataItemExpectation.getName()),
                                            reportIdentifier,
                                            reportExpectation.getDataSetName(),
                                            reportableEvent,
                                            dataItemExpectation);
                                }));
    }

    private List<ReportField> filterEmptyReportFields(List<ReportField> results) {
        return results.stream()
                .filter(r -> !isEmpty(r.getValue()))
                .collect(Collectors.toList());
    }

    private boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }

    private static <T> T readFile(URL u, ObjectMapper mapper, Class<T> clazz) {
        try {
            return mapper.readValue(UrlUtils.openURL(u), clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void assertJsonEquals(String expectedJson, String resultJson) {
        assertEquals(
                normaliseLineEndings(expectedJson),
                normaliseLineEndings(resultJson));
    }

    private String normaliseLineEndings(String str) {
        return Optional.ofNullable(str)
                .map(s -> s.replace("\r", ""))
                .orElse(null);
    }
}
