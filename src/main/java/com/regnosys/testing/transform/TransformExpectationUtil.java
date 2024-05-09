package com.regnosys.testing.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.Multimap;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TestPackModel.SampleModel;
import com.regnosys.testing.TestingExpectationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TransformExpectationUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformExpectationUtil.class);

    private final static ObjectReader ROSETTA_OBJECT_READER =
            RosettaObjectMapper
                    .getNewRosettaObjectMapper()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .reader();

    public static void writeExpectations(Multimap<String, TransformTestResult> actualExpectation, Path testPackConfigPath) throws JsonProcessingException {
        if (!TestingExpectationUtil.WRITE_EXPECTATIONS) {
            LOGGER.info("WRITE_EXPECTATIONS is set to false, not updating expectations.");
            return;
        }
        for (var entry : actualExpectation.asMap().entrySet()) {
            String testPackId = entry.getKey();
            TestPackModel model = getTestPackModel(testPackId, TransformExpectationUtil.class.getClassLoader(), testPackConfigPath);

            Collection<TransformTestResult> transformTestResults = entry.getValue();
            List<SampleModel> sampleModelList = transformTestResults.stream()
                    .map(TransformTestResult::getSampleModel)
                    .sorted(Comparator.comparing(SampleModel::getId))
                    .collect(Collectors.toList());
            TestPackModel testPackModel = new TestPackModel(model.getId(), model.getPipelineId(), model.getName(), sampleModelList);
            String configFileContent = TestingExpectationUtil.EXPECTATIONS_WRITER.writeValueAsString(testPackModel);

            // Add environment variable TEST_WRITE_BASE_PATH to override the base write path, e.g.
            // TEST_WRITE_BASE_PATH=/Users/hugohills/code/src/github.com/REGnosys/rosetta-cdm/src/main/resources/
            TestingExpectationUtil.TEST_WRITE_BASE_PATH
                    .filter(Files::exists)
                    .ifPresent(writeBasePath -> {
                        // 1. write new test pack config file
                        Path configFileWritePath = writeBasePath.resolve(testPackConfigPath).resolve(testPackModel.getId() + ".json");
                        TestingExpectationUtil.writeFile(configFileWritePath, configFileContent, TestingExpectationUtil.CREATE_EXPECTATION_FILES);

                        // 2. write new output json/xml
                        transformTestResults.stream()
                                .forEach(r -> TestingExpectationUtil.writeFile(writeBasePath.resolve(r.getSampleModel().getOutputPath()), r.getOutput(), TestingExpectationUtil.CREATE_EXPECTATION_FILES));
                    });
        }
    }

    private static TestPackModel getTestPackModel(String testPackId, ClassLoader classLoader, Path resourcePath) {
        List<URL> testPackUrls = TestingExpectationUtil.findPaths(resourcePath, classLoader, "test-pack-.*\\.json");
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return testPackUrls.stream()
                .map(url -> TestingExpectationUtil.readFile(url, mapper, TestPackModel.class))
                .filter(testPackModel -> testPackModel.getId() != null)
                .filter(testPackModel -> testPackModel.getId().equals(testPackId))
                .findFirst()
                .orElseThrow();
    }
}
