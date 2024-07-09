package com.regnosys.testing.pipeline;

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

        assertFileExists(tempDir, "enrich/config/pipeline-enrich-start.json");
        assertFileExists(tempDir, "regulatory-reporting/config/pipeline-report-start-middle-b.json");
        assertFileExists(tempDir, "regulatory-reporting/config/pipeline-report-start-middle-a.json");
        assertFileExists(tempDir, "projection/config/pipeline-projection-start-middle-a-end-a.json");
        assertFileExists(tempDir, "projection/config/pipeline-projection-start-middle-b-end-b.json");
        assertFileExists(tempDir, "projection/config/pipeline-projection-start-middle-b-end-a.json");
        assertFileExists(tempDir, "projection/config/pipeline-projection-start-middle-a-end-b.json");
    }

    private static void assertFileExists(Path tempDir, String fileName) {
        assertTrue(Files.exists(tempDir.resolve(fileName)), String.format("File not found %s", fileName));
    }
}
