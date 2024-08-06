package com.regnosys.testing.pipeline;

/*-
 * ===============
 * Rune Testing
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
 * ===============
 */

import com.regnosys.rosetta.common.transform.TransformType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PipelineTestPackWriterTest {


    @Inject
    private PipelineTestPackWriter pipelineTestPackWriter;

    @Inject
    PipelineTestHelper helper;

    @BeforeEach
    void setUp() {
        PipelineTestHelper.setupInjector(this);
    }

    @Test
    void writeTestPacksForNestedTreeConfig(@TempDir Path tempDir) throws Exception {
        Path inputPath = Files.createDirectories(tempDir.resolve(TransformType.ENRICH.getResourcePath()).resolve("input"));

        Path testPack1Path = Files.createDirectories(inputPath.resolve("test-pack-1"));
        Path testPack2Path = Files.createDirectories(inputPath.resolve("test-pack-2"));

        Files.write(testPack1Path.resolve("sample-1-1.json"), "{\"name\": \"1-1\"}".getBytes());
        Files.write(testPack1Path.resolve("sample-1-2.json"), "{\"name\": \"1-2\"}".getBytes());
        Files.write(testPack2Path.resolve("sample-2-1.json"), "{\"name\": \"2-1\"}".getBytes());
        Files.write(testPack2Path.resolve("sample-2-2.json"), "{\"name\": \"2-2\"}".getBytes());

        PipelineTreeConfig chain = helper.createNestedTreeConfig().strictUniqueIds().withWritePath(tempDir);
        pipelineTestPackWriter.writeTestPacks(chain);

        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-start-middle-a-test-pack-2.json");
        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-start-middle-b-test-pack-1.json");
        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-start-middle-b-test-pack-2.json");
        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-start-middle-a-test-pack-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-b/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-b/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-b/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-b/test-pack-1/sample-1-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-a/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-a/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-a/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-a/test-pack-1/sample-1-2.json");
        assertFileExists(tempDir, "enrich/config/test-pack-enrich-start-test-pack-1.json");
        assertFileExists(tempDir, "enrich/config/test-pack-enrich-start-test-pack-2.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack-1/sample-1-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-a-end-a-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-b-end-b-test-pack-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-a-end-b-test-pack-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-b-end-a-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-b-end-a-test-pack-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-a-end-b-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-b-end-b-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-a-end-a-test-pack-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-a/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-a/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-a/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-a/test-pack-1/sample-1-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-b/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-b/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-b/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-b/test-pack-1/sample-1-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-a/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-a/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-a/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-a/test-pack-1/sample-1-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-b/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-b/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-b/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-b/test-pack-1/sample-1-2.json");
    }

    @Test
    void writeTestPacksForTreeConfig(@TempDir Path tempDir) throws Exception {
        Path inputPath = Files.createDirectories(tempDir.resolve(TransformType.ENRICH.getResourcePath()).resolve("input"));

        Path testPack1Path = Files.createDirectories(inputPath.resolve("test-pack-1"));
        Path testPack2Path = Files.createDirectories(inputPath.resolve("test-pack-2"));

        Files.write(testPack1Path.resolve("sample-1-1.json"), "{\"name\": \"1-1\"}".getBytes());
        Files.write(testPack1Path.resolve("sample-1-2.json"), "{\"name\": \"1-2\"}".getBytes());
        Files.write(testPack2Path.resolve("sample-2-1.json"), "{\"name\": \"2-1\"}".getBytes());
        Files.write(testPack2Path.resolve("sample-2-2.json"), "{\"name\": \"2-2\"}".getBytes());

        PipelineTreeConfig chain = helper.createTreeConfig().strictUniqueIds().withWritePath(tempDir);
        pipelineTestPackWriter.writeTestPacks(chain);


        assertFileExists(tempDir, "enrich/config/test-pack-enrich-start-test-pack-1.json");
        assertFileExists(tempDir, "enrich/config/test-pack-enrich-start-test-pack-2.json");
        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-start-middle-test-pack-1.json");
        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-start-middle-test-pack-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-end-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-end-test-pack-2.json");

        assertFileExists(tempDir, "regulatory-reporting/output/start/middle/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle/test-pack-1/sample-1-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle/test-pack-1/sample-1-2.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack-1/sample-1-2.json");

        assertFileExists(tempDir, "projection/output/start/middle/end/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/start/middle/end/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/start/middle/end/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/start/middle/end/test-pack-1/sample-1-2.json");

    }

    @Test
    void writeTestPacksForTreeConfigWithMultipleStartNodes(@TempDir Path tempDir) throws Exception {
        Path inputPath = Files.createDirectories(tempDir.resolve(TransformType.REPORT.getResourcePath()).resolve("input"));

        Path testPack1Path = Files.createDirectories(inputPath.resolve("test-pack-1"));
        Path testPack2Path = Files.createDirectories(inputPath.resolve("test-pack-2"));

        Files.write(testPack1Path.resolve("sample-1-1.json"), "{\"name\": \"1-1\"}".getBytes());
        Files.write(testPack1Path.resolve("sample-1-2.json"), "{\"name\": \"1-2\"}".getBytes());
        Files.write(testPack2Path.resolve("sample-2-1.json"), "{\"name\": \"2-1\"}".getBytes());
        Files.write(testPack2Path.resolve("sample-2-2.json"), "{\"name\": \"2-2\"}".getBytes());

        PipelineTreeConfig chain = helper.createNestedTreeConfigMultipleStartingNodes().strictUniqueIds().withWritePath(tempDir);
        pipelineTestPackWriter.writeTestPacks(chain);

        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-middle-a-test-pack-1.json");
        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-middle-a-test-pack-2.json");
        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-middle-b-test-pack-1.json");
        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-middle-b-test-pack-2.json");

        assertFileExists(tempDir, "projection/config/test-pack-projection-middle-a-end-a-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-middle-b-end-b-test-pack-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-middle-a-end-b-test-pack-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-middle-b-end-a-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-middle-b-end-a-test-pack-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-middle-a-end-b-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-middle-b-end-b-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-middle-a-end-a-test-pack-2.json");

        assertFileExists(tempDir, "regulatory-reporting/output/middle-a/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/middle-a/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/middle-a/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/middle-a/test-pack-1/sample-1-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/middle-a/test-pack-2/sample-2-2.json");

        assertFileExists(tempDir, "regulatory-reporting/output/middle-b/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/middle-b/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/middle-b/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/middle-b/test-pack-1/sample-1-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/middle-b/test-pack-2/sample-2-2.json");


        assertFileExists(tempDir, "projection/output/middle-a/end-a/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/middle-a/end-a/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/middle-a/end-a/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/middle-a/end-a/test-pack-1/sample-1-2.json");

        assertFileExists(tempDir, "projection/output/middle-a/end-b/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/middle-a/end-b/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/middle-a/end-b/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/middle-a/end-b/test-pack-1/sample-1-2.json");

        assertFileExists(tempDir, "projection/output/middle-b/end-a/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/middle-b/end-a/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/middle-b/end-a/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/middle-b/end-a/test-pack-1/sample-1-2.json");

        assertFileExists(tempDir, "projection/output/middle-b/end-b/test-pack-2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/middle-b/end-b/test-pack-2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/middle-b/end-b/test-pack-1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/middle-b/end-b/test-pack-1/sample-1-2.json");

    }

    private static void assertFileExists(Path tempDir, String fileName) {
        assertTrue(Files.exists(tempDir.resolve(fileName)), String.format("File not found %s", fileName));
    }


}
