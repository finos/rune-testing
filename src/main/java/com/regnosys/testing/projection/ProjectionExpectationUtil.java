package com.regnosys.testing.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Multimap;
import com.regnosys.testing.TestingExpectationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ProjectionExpectationUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectionExpectationUtil.class);

    public static void writeExpectations(Multimap<ProjectionNameAndDataSetName, ProjectionTestResult> actualExpectation, Path outputPath) throws JsonProcessingException {
        if (!TestingExpectationUtil.WRITE_EXPECTATIONS) {
            LOGGER.info("WRITE_EXPECTATIONS is set to false, not updating expectations.");
            return;
        }

        for (var entry : actualExpectation.asMap().entrySet()) {
            ProjectionNameAndDataSetName projectionNameAndDataSetName = entry.getKey();
            String projectionName = projectionNameAndDataSetName.projectionName;
            String dataSetName = projectionNameAndDataSetName.dataSetName;
            Path projectionExpectationPath = projectionNameAndDataSetName.projectExpectationFilePath;

            Collection<ProjectionTestResult> projectionTestResults = entry.getValue();

            List<ProjectionDataItemExpectation> dataItemExpectations = projectionTestResults.stream()
                    .map(testResult ->
                            new ProjectionDataItemExpectation(testResult.getInputFileName(),
                                    testResult.getKeyValueFileName(),
                                    testResult.getOutputFileName(),
                                    testResult.getValidationFailures().getActual(),
                                    testResult.getValidXml().getActual(),
                                    testResult.getError().getActual()))
                    .sorted()
                    .collect(Collectors.toList());

            ProjectionDataSetExpectation projectionDataSetExpectation = new ProjectionDataSetExpectation(projectionName, dataSetName, dataItemExpectations);
            String expectationFileContent = TestingExpectationUtil.EXPECTATIONS_WRITER.writeValueAsString(projectionDataSetExpectation);

            // Add environment variable TEST_WRITE_BASE_PATH to override the base write path, e.g.
            // TEST_WRITE_BASE_PATH=/Users/hugohills/dev/github/rosetta-models/digital-regulatory-reporting/rosetta-source/src/main/resources
            TestingExpectationUtil.TEST_WRITE_BASE_PATH
                    .filter(Files::exists)
                    .ifPresent(writeBasePath -> {
                        // 1. write new expectations file
                        Path expectationFileWritePath = writeBasePath.resolve(projectionExpectationPath);
                        TestingExpectationUtil.writeFile(expectationFileWritePath, expectationFileContent, TestingExpectationUtil.CREATE_EXPECTATION_FILES);

                        // 2. write new key-value json
                        projectionTestResults.stream()
                                .map(ProjectionTestResult::getKeyValue)
                                .forEach(r -> TestingExpectationUtil.writeFile(writeBasePath.resolve(r.getExpectationPath()), r.getActual(), TestingExpectationUtil.CREATE_EXPECTATION_FILES));

                        // 3. write new report json
                        projectionTestResults.stream()
                                .map(ProjectionTestResult::getOutput)
                                .forEach(r -> {
                                    TestingExpectationUtil.writeFile(writeBasePath.resolve(r.getExpectationPath()), r.getActual(), TestingExpectationUtil.CREATE_EXPECTATION_FILES);
                                });

                    });
        }

    }
}
