package com.regnosys.testing.pipeline;

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
    void writeTestPacks(@TempDir Path tempDir) throws Exception {
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

    private static void assertFileExists(Path tempDir, String fileName) {
        assertTrue(Files.exists(tempDir.resolve(fileName)), String.format("File not found %s", fileName));
    }


}
