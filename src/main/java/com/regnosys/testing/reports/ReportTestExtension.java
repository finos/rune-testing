package com.regnosys.testing.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.hashing.ReferenceResolverProcessStep;
import com.regnosys.rosetta.common.reports.RegReportIdentifier;
import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.regnosys.rosetta.common.reports.RegReportUseCase;
import com.regnosys.rosetta.common.reports.ReportField;
import com.regnosys.rosetta.common.serialisation.RosettaDataValueObjectToString;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.testing.reports.FileNameProcessor.removeFileExtension;
import static com.regnosys.testing.reports.FileNameProcessor.removeFilePrefix;
import static com.regnosys.testing.reports.ReportExpectationUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ReportTestExtension<T extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportTestExtension.class);
    private final Module runtimeModule;
    private final ImmutableList<String> rosettaPaths;
    private final Class<T> inputType;
    private Path rootExpectationsPath;

    private List<RegReportIdentifier> reportIdentifiers;
    @Inject
    private RosettaTypeValidator typeValidator;
    @Inject
    private ReportUtil reportUtil;
    @Inject
    private ReferenceConfig referenceConfig;

    private Multimap<ReportIdentifierAndDataSetName, ReportTestResult> actualExpectation;

    public ReportTestExtension(Module runtimeModule, ImmutableList<String> rosettaPaths, Class<T> inputType) {
        this.runtimeModule = runtimeModule;
        this.rosettaPaths = rosettaPaths;
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
        reportIdentifiers = reportUtil.loadRegReportIdentifier(rosettaPaths);
    }

    public <In extends RosettaModelObject, Out extends RosettaModelObject> void assertTest(
            RegReportIdentifier reportIdentifier,
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
                field -> field.accept(flattener, null)
        );
        List<ReportField> results = flattener.accumulator;
        Path keyValueExpectationPath = RegReportPaths.getKeyValueExpectationFilePath(outputPath, reportIdentifier, dataSetName, inputFileName);
        // For the tabulated values, we need to sort and remove duplicates.
        ExpectedAndActual<String> keyValue = getSortedExpectedAndActual(
                keyValueExpectationPath,
                results.stream()
                        .collect(Collectors.toMap(ReportField::getName, (f) -> f, (a, b) -> {
                            // Remove duplicate fields, but throw if they don't have the same value.
                            if (!a.getValue().equals(b.getValue())) {
                                throw new IllegalStateException("Duplicate fields with different values.\n" + a + "\n" + b);
                            }
                            return a;
                        }))
                        .values(),
                Comparator.comparing(ReportField::getName));

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

        assertJsonEquals(keyValue.getExpected(), keyValue.getActual());
        assertJsonEquals(report.getExpected(), report.getActual());
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
        List<URL> expectationFiles = readReportExpectationsFromPath(rootExpectationsPath, ReportTestExtension.class.getClassLoader());
        return getArguments(expectationFiles);
    }

    public Stream<Arguments> getArguments(List<URL> expectationFiles) {
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return expectationFiles.stream()
                // report-data-set expectation contains all expectations for a data-set (e.g. a test pack)
                .map(reportExpectationUrl -> readFile(reportExpectationUrl, mapper, ReportDataSetExpectation.class))
                .flatMap(reportExpectation ->
                        // report-data-item expectation contains expectations of a single input file
                        reportExpectation.getDataItemExpectations().stream()
                                .map(dataItemExpectation -> {
                                    // input file to be tested
                                    String fileName = dataItemExpectation.getFileName();
                                    URL inputFileUrl = Resources.getResource(fileName);
                                    // deserialise into input (e.g. ReportableEvent)
                                    T input = readFile(inputFileUrl, mapper, inputType);
                                    // get the report identifier
                                    String reportName = reportExpectation.getReportName();
                                    RegReportIdentifier reportIdentifier = reportIdentifiers.stream()
                                            .filter(r -> r.getName().equals(reportName))
                                            .findFirst().orElseThrow();
                                    // data item name
                                    String inputName = removeFileExtension(removeFilePrefix(fileName)).replace("-", " ");
                                    return Arguments.of(
                                            String.format("%s | %s", reportName, inputName),
                                            reportIdentifier,
                                            reportExpectation.getDataSetName(),
                                            input,
                                            dataItemExpectation);
                                }));
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

    public Module getRuntimeModule() {
        return runtimeModule;
    }

    public List<RegReportIdentifier> getReportIdentifiers() {
        return reportIdentifiers;
    }

    private static class FieldValueFlattener implements Tabulator.FieldValueVisitor<Integer> {
        public List<ReportField> accumulator = new ArrayList<>();

        @Override
        public void visitSingle(Tabulator.FieldValue fieldValue, Integer index) {
            if (fieldValue.getValue().isPresent()) {
                String value = RosettaDataValueObjectToString.toValueString(fieldValue.getValue().get());
                if (!value.isEmpty()) {
                    accumulator.add(new ReportField(
                            insertIndex(fieldValue.getField().getName(), index),
                            ((Tabulator.FieldImpl)fieldValue.getField()).getRuleId().map(id -> id.getNamespace().child("blueprint").child(id.getName() + "Rule").withDots()).orElse(null),
                            index,
                            value,
                            ""
                    ));
                }
            }
        }
        private static String insertIndex(String fieldName, Integer index) {
            if (index == null) {
                return fieldName;
            }
            if (fieldName.contains("$")) {
                return fieldName.replace("$", index.toString());
            }
            return fieldName + " (" + index + ")";
        }
        @Override
        public void visitNested(Tabulator.NestedFieldValue nestedFieldValue, Integer index) {
            nestedFieldValue.getValue().ifPresent(
                    (v) -> v.forEach(
                            sub -> sub.accept(this, null)
                    )
            );
        }
        @Override
        public void visitMultiNested(Tabulator.MultiNestedFieldValue multiNestedFieldValue, Integer index) {
            multiNestedFieldValue.getValue().ifPresent(
                    (vs) -> {
                        for (int i=0; i<vs.size(); i++) {
                            int repeatableIndex = i + 1;
                            vs.get(i).forEach(
                                    sub -> sub.accept(this, repeatableIndex)
                            );
                        }
                    }
            );
        }
    }
}
