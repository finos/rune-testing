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

public class PipelineModelWriterTest {

    @Inject
    private PipelineModelWriter pipelineModelWriter;


    @Inject
    PipelineTestHelper helper;

    @BeforeEach
    void setUp() {
        PipelineTestHelper.setupInjector(this);
    }

    @Test
    void writeTestPacks(@TempDir Path tempDir) throws Exception {
        Path inputPath = Files.createDirectories(tempDir.resolve(TransformType.ENRICH.getResourcePath()).resolve("input"));

        Path testPack1Path = Files.createDirectories(inputPath.resolve("test-pack-1"));
        Path testPack2Path = Files.createDirectories(inputPath.resolve("test-pack-2"));

        Files.write(testPack1Path.resolve("sample-1-1.json"), "{\"name\": \"1-1\"}".getBytes());
        Files.write(testPack1Path.resolve("sample-1-2.json"), "{\"name\": \"1-2\"}".getBytes());
        Files.write(testPack2Path.resolve("sample-2-1.json"), "{\"name\": \"2-1\"}".getBytes());
        Files.write(testPack2Path.resolve("sample-2-2.json"), "{\"name\": \"2-2\"}".getBytes());

        PipelineTreeConfig chain = helper.createNestedTreeConfig().strictUniqueIds().withWritePath(tempDir);
        pipelineModelWriter.writePipelines(chain);

        assertFileExists(tempDir, "enrich/config/pipeline-enrich-testPrefix-start.json");
        assertFileExists(tempDir, "regulatory-reporting/config/pipeline-report-testPrefix-start-middle-b.json");
        assertFileExists(tempDir, "regulatory-reporting/config/pipeline-report-testPrefix-start-middle-a.json");
        assertFileExists(tempDir, "projection/config/pipeline-projection-testPrefix-start-middle-a-end-a.json");
        assertFileExists(tempDir, "projection/config/pipeline-projection-testPrefix-start-middle-b-end-b.json");
        assertFileExists(tempDir, "projection/config/pipeline-projection-testPrefix-start-middle-b-end-a.json");
        assertFileExists(tempDir, "projection/config/pipeline-projection-testPrefix-start-middle-a-end-b.json");
    }

    private static void assertFileExists(Path tempDir, String fileName) {
        assertTrue(Files.exists(tempDir.resolve(fileName)), String.format("File not found %s", fileName));
    }
}
