package com.regnosys.testing.reports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.Multimap;
import com.regnosys.rosetta.common.reports.RegReportIdentifier;
import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.regnosys.rosetta.common.reports.ReportField;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.testing.ExpectationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ReportExpectationUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportExpectationUtil.class);

    private final static ObjectReader ROSETTA_OBJECT_READER =
            RosettaObjectMapper
                    .getNewRosettaObjectMapper()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .reader();

    public static void writeExpectations(Multimap<ReportIdentifierAndDataSetName, ReportTestResult> actualExpectation) throws JsonProcessingException {
        if (!ExpectationUtil.WRITE_EXPECTATIONS) {
            LOGGER.info("WRITE_EXPECTATIONS is set to false, not updating expectations.");
            return;
        }
        for (var entry : actualExpectation.asMap().entrySet()) {
            ReportIdentifierAndDataSetName key = entry.getKey();
            RegReportIdentifier reportIdentifier = key.reportIdentifier;
            String dataSetName = key.dataSetName;

            Collection<ReportTestResult> reportTestExpectations = entry.getValue();
            List<ReportDataItemExpectation> dataItemExpectations = reportTestExpectations.stream()
                    .map(x -> new ReportDataItemExpectation(x.getInputFileName(), x.getValidationFailures().getActual()))
                    .sorted()
                    .collect(Collectors.toList());
            ReportDataSetExpectation reportDataSetExpectation = new ReportDataSetExpectation(reportIdentifier.getName(), dataSetName, dataItemExpectations);
            String expectationFileContent = ExpectationUtil.EXPECTATIONS_WRITER.writeValueAsString(reportDataSetExpectation);

            Path outputPath = RegReportPaths.getDefault().getOutputRelativePath();
            // Add environment variable TEST_WRITE_BASE_PATH to override the base write path, e.g.
            // TEST_WRITE_BASE_PATH=/Users/hugohills/code/src/github.com/REGnosys/rosetta-cdm/src/main/resources/
            ExpectationUtil.TEST_WRITE_BASE_PATH
                    .filter(Files::exists)
                    .ifPresent(writeBasePath -> {
                        // 1. write new expectations file
                        Path expectationFileWritePath = writeBasePath.resolve(RegReportPaths.getReportExpectationsFilePath(outputPath, reportIdentifier, dataSetName));
                        ExpectationUtil.writeFile(expectationFileWritePath, expectationFileContent, ExpectationUtil.CREATE_EXPECTATION_FILES);

                        // 2. write new key-value json
                        reportTestExpectations.stream()
                                .map(ReportTestResult::getKeyValue)
                                .forEach(r -> ExpectationUtil.writeFile(writeBasePath.resolve(r.getExpectationPath()), r.getActual(), ExpectationUtil.CREATE_EXPECTATION_FILES));

                        // 3. write new report json
                        reportTestExpectations.stream()
                                .map(ReportTestResult::getReport)
                                .forEach(r -> ExpectationUtil.writeFile(writeBasePath.resolve(r.getExpectationPath()), r.getActual(), ExpectationUtil.CREATE_EXPECTATION_FILES));
                    });
        }
    }

    public static <T> ExpectedAndActual<String> getSortedExpectedAndActual(Path expectationPath, Collection<T> results, Comparator<? super T> comparator) throws IOException {
        List<T> sorted = new ArrayList<>(results);
        sorted.sort(comparator);
        String actualJson = ExpectationUtil.ROSETTA_OBJECT_WRITER.writeValueAsString(sorted);
        String expectedJson = Optional.ofNullable(ExpectationUtil.readStringFromResources(expectationPath))
                .map(expected -> {
                    try {
                        List<ReportField> expectedFields = new ArrayList<>(List.of(ROSETTA_OBJECT_READER.readValue(expected, ReportField[].class)));
                        expectedFields.sort(Comparator.comparing(ReportField::getName));
                        return ExpectationUtil.ROSETTA_OBJECT_WRITER.writeValueAsString(expectedFields);
                    } catch (IOException e) {
                        LOGGER.error("Failed to read expected {}", expected, e);
                        return null;
                    }
                })
                .orElse(null);
        return new ExpectedAndActual<>(expectationPath, expectedJson, actualJson);
    }

}
