package com.regnosys.testing.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Multimap;
import com.regnosys.testing.ExpectationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ProjectExpectationUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectExpectationUtil.class);
    public static void writeExpectations(Multimap<ProjectNameAndDataSetName, ProjectTestResult> actualExpectation, Path outputPath) throws JsonProcessingException {
        if (!ExpectationUtil.WRITE_EXPECTATIONS) {
            LOGGER.info("WRITE_EXPECTATIONS is set to false, not updating expectations.");
            return;
        }

        for (var entry : actualExpectation.asMap().entrySet()) {
            ProjectNameAndDataSetName projectNameAndDataSetName = entry.getKey();
            String projectName = projectNameAndDataSetName.projectName;
            String dataSetName = projectNameAndDataSetName.dataSetName;
            Path projectExpectationPath = projectNameAndDataSetName.projectExpectationFilePath;

            Collection<ProjectTestResult> projectTestResults = entry.getValue();

            List<ProjectDataItemExpectation> dataItemExpectations = projectTestResults.stream()
                    .map(testResult -> new ProjectDataItemExpectation(testResult.getInputFileName(), testResult.getOutputFileName(), testResult.getValidationFailures().getActual()))
                    .sorted()
                    .collect(Collectors.toList());

            ProjectDataSetExpectation projectDataSetExpectation = new ProjectDataSetExpectation(projectName, dataSetName, dataItemExpectations);
            String expectationFileContent = ExpectationUtil.EXPECTATIONS_WRITER.writeValueAsString(projectDataSetExpectation);

            // Add environment variable TEST_WRITE_BASE_PATH to override the base write path, e.g.
            // TEST_WRITE_BASE_PATH=/Users/hugohills/code/src/github.com/REGnosys/rosetta-cdm/src/main/resources/
            ExpectationUtil.TEST_WRITE_BASE_PATH
                    .filter(Files::exists)
                    .ifPresent(writeBasePath -> {
                        // 1. write new expectations file
                        Path expectationFileWritePath = writeBasePath.resolve(projectExpectationPath);
                        ExpectationUtil.writeFile(expectationFileWritePath, expectationFileContent, ExpectationUtil.CREATE_EXPECTATION_FILES);

                        // 2. write new report json
                        projectTestResults.stream()
                                .map(ProjectTestResult::getReport)
                                .forEach(r -> {
                                    ExpectationUtil.writeFile(writeBasePath.resolve(r.getExpectationPath()), r.getActual(), ExpectationUtil.CREATE_EXPECTATION_FILES);
                                });
                    });
        }

    }
}
