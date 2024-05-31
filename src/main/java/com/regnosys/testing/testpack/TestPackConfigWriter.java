package com.regnosys.testing.testpack;

/*-
 * ==============
 * Rosetta Testing
 * ===============
 * Copyright (C) 2022 - 2024 REGnosys
 * ===============
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==============
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TestPackModel.SampleModel;
import com.regnosys.testing.reports.FileNameProcessor;
import com.regnosys.testing.reports.FilterProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestPackConfigWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestPackConfigWriter.class);

    private final ObjectMapper writeMapper;
    private final SimpleFilterProvider filterProvider;

    public TestPackConfigWriter(ObjectMapper writeMapper) {
        this.writeMapper = writeMapper;
        this.filterProvider = FilterProvider.getExpectedTypeFilter();
    }

    public void sortAndWriteConfigFile(Path resourcesPath, Path configPath, TestPackModel testPackModel) {
        TestPackModel sortedTestPackModel = sortSamples(testPackModel);
        writeConfigFile(resourcesPath, configPath, testPackModel.getId(), sortedTestPackModel);
    }

    public void writeConfigFile(Path resourcesPath, Path configPath, String id, Object object) {
        Path path = generateTestPackModelFilePath(configPath, id);

        try {
            Path fullPath = resourcesPath.resolve(path);
            Files.createDirectories(fullPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(fullPath)) {
                writer.write(
                        writeMapper.writer(filterProvider)
                                .withDefaultPrettyPrinter()
                                .writeValueAsString(object)
                );
                LOGGER.info("Writing config file: {}", path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TestPackModel sortSamples(TestPackModel testPackModel) {
        List<SampleModel> sampleModels = testPackModel.getSamples()
                .stream()
                .sorted(Comparator.comparing(SampleModel::getId))
                .collect(Collectors.toList());

        return new TestPackModel(testPackModel.getId(),
                testPackModel.getPipelineId(),
                testPackModel.getName(),
                sampleModels);
    }

    private List<TestPackModel> readTestPackModelFile(Path file) {
        try {
            return writeMapper.readValue(file.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Deprecated // is this used?
    public Map<Path, List<TestPackModel>> readAllTestPackConfigFiles(Path testPackModelParentDirectory) {
        try {
            return Files.walk(testPackModelParentDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("test-pack-"))
                    .collect(Collectors.toMap(
                            path -> path,
                            this::readTestPackModelFile
                    ));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path generateTestPackModelFilePath(Path outFolder, String Filename) {
        return outFolder.resolve(FileNameProcessor.sanitizeFileName(Filename + ".json"));
    }
}
