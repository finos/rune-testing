package com.regnosys.testing.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Multimap;
import com.regnosys.rosetta.common.projection.ProjectionDataItemExpectation;
import com.regnosys.rosetta.common.projection.ProjectionDataSetExpectation;
import com.regnosys.rosetta.common.projection.RegProjectionPaths;
import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.testing.TestingExpectationUtil;
import com.regnosys.testing.transform.TestPackAndDataSetName;
import com.regnosys.testing.transform.TransformTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ProjectionExpectationUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectionExpectationUtil.class);

    public static void writeExpectations(Multimap<TestPackAndDataSetName, TransformTestResult> actualExpectation) throws JsonProcessingException {
        if (!TestingExpectationUtil.WRITE_EXPECTATIONS) {
            LOGGER.info("WRITE_EXPECTATIONS is set to false, not updating expectations.");
            return;
        }
        for (var entry : actualExpectation.asMap().entrySet()) {
            TestPackAndDataSetName key = entry.getKey();

            Collection<TransformTestResult> transformTestResults = entry.getValue();
            List<TestPackModel.SampleModel> sampleModelList = transformTestResults.stream()
                    .map(x -> new TestPackModel.SampleModel(x.getSampleModel().getId(),
                                    x.getSampleModel().getName(),
                                    x.getSampleModel().getInputPath(),
                                    x.getSampleModel().getOutputPath(),
                                    x.getSampleModel().getOutputTabulatedPath(),
                                    new TestPackModel.SampleModel.Assertions(
                                            x.getModelValidationFailures().getActual(),
                                            x.getSchemaValidationFailure().getActual(),
                                            x.getRuntimeError().getActual()
                                    )
                            )
                    )
                    .sorted(Comparator.comparing(TestPackModel.SampleModel::getId))
                    .collect(Collectors.toList());
            TestPackModel testPackModel = new TestPackModel(key.getTestPackID(), key.getPipeLineId(), key.getDataSetName(), sampleModelList);
            String expectationFileContent = TestingExpectationUtil.EXPECTATIONS_WRITER.writeValueAsString(testPackModel);

            Path configPath = RegProjectionPaths.getProjectionPath().getConfigRelativePath();
            // Add environment variable TEST_WRITE_BASE_PATH to override the base write path, e.g.
            // TEST_WRITE_BASE_PATH=/Users/hugohills/code/src/github.com/REGnosys/rosetta-cdm/src/main/resources/
            TestingExpectationUtil.TEST_WRITE_BASE_PATH
                    .filter(Files::exists)
                    .ifPresent(writeBasePath -> {
                        // 1. write new expectations file
                        Path expectationFileWritePath = writeBasePath.resolve(configPath).resolve(testPackModel.getId() + ".json");
                        TestingExpectationUtil.writeFile(expectationFileWritePath, expectationFileContent, TestingExpectationUtil.CREATE_EXPECTATION_FILES);

                        // 2. write new key-value json
                        transformTestResults.stream()
                                .map(TransformTestResult::getKeyValue)
                                .forEach(r -> TestingExpectationUtil.writeFile(writeBasePath.resolve(r.getExpectationPath()), r.getActual(), TestingExpectationUtil.CREATE_EXPECTATION_FILES));

                        // 3. write new report json
                        transformTestResults.stream()
                                .map(TransformTestResult::getReport)
                                .forEach(r -> TestingExpectationUtil.writeFile(writeBasePath.resolve(r.getExpectationPath()), r.getActual(), TestingExpectationUtil.CREATE_EXPECTATION_FILES));
                    });
        }
    }
}
