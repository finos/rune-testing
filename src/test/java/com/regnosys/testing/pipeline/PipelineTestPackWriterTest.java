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

import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import static com.regnosys.testing.pipeline.PipelineFilter.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void createCsvSampleFiles(@TempDir Path tempDir) throws IOException {
        Path inputPath = Files.createDirectories(tempDir.resolve(TransformType.TRANSLATE.getResourcePath()).resolve("input"));

        Path testPackPath = Files.createDirectories(inputPath.resolve("csv-test-pack"));
        Path csvTestPackSourceFile = testPackPath.resolve("csv-samples.csv");
        Files.write(csvTestPackSourceFile, "name,age\nname1,age1\nname2,age2\nname3,age3".getBytes());

        PipelineTreeConfig chain = helper.createCsvConfig(Path.of("ingest/input/csv-test-pack/csv-samples.csv")).strictUniqueIds().withWritePath(tempDir);
        pipelineTestPackWriter.writeTestPacks(chain);

        assertFileExists(tempDir, "ingest/config/test-pack-translate-start-csv-test-pack.json");
        assertFileExists(tempDir, "ingest/input/csv-test-pack/csv-samples_1.csv");
        assertFileExists(tempDir, "ingest/input/csv-test-pack/csv-samples_2.csv");
        assertFileExists(tempDir, "ingest/input/csv-test-pack/csv-samples_3.csv");
        assertFileExists(tempDir, "ingest/input/csv-test-pack/csv-samples.csv");
        assertFileExists(tempDir, "ingest/output/start/csv-test-pack/csv-samples_1.json");
        assertFileExists(tempDir, "ingest/output/start/csv-test-pack/csv-samples_2.json");
        assertFileExists(tempDir, "ingest/output/start/csv-test-pack/csv-samples_3.json");
        assertFileDoesNotExist(tempDir, "ingest/output/start/csv-test-pack/csv-samples.json");

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

    @Test
    void writeTestPacksForTreeConfigWithFilter(@TempDir Path tempDir) throws Exception {
        Path inputPath = Files.createDirectories(tempDir.resolve(TransformType.REPORT.getResourcePath()).resolve("input"));

        Path testPack1Path = Files.createDirectories(inputPath.resolve("test-pack-1"));
        Path testPack2Path = Files.createDirectories(inputPath.resolve("test-pack-2"));
        Path testPackAPath = Files.createDirectories(inputPath.resolve("test-pack-a"));
        Path testPackBPath = Files.createDirectories(inputPath.resolve("test-pack-b"));
        Path testPackCPath = Files.createDirectories(inputPath.resolve("test-pack-only-c"));

        Files.write(testPack1Path.resolve("sample-1-1.json"), "{\"name\": \"1-1\"}".getBytes());
        Files.write(testPack2Path.resolve("sample-1-1.json"), "{\"name\": \"1-1\"}".getBytes());
        Files.write(testPackAPath.resolve("sample-1-1.json"), "{\"name\": \"1-1\"}".getBytes());
        Files.write(testPackBPath.resolve("sample-1-1.json"), "{\"name\": \"1-1\"}".getBytes());
        Files.write(testPackCPath.resolve("sample-1-1.json"), "{\"name\": \"1-1\"}".getBytes());

        PipelineTreeConfig chain = new PipelineTreeConfig()
                .withTestPackIdFilter(startsWith("test-pack-a", "test-pack-1", "test-pack-2").
                        and(Predicate.not(startsWith("test-pack-b", "test-pack-only-c"))))
                .starting(TransformType.REPORT, helper.middleAClass())
                .add(helper.middleAClass(), TransformType.PROJECTION, helper.endAClass())
                .strictUniqueIds()
                .withWritePath(tempDir);
        pipelineTestPackWriter.writeTestPacks(chain);

        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-middle-a-test-pack-1.json");
        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-middle-a-test-pack-2.json");
        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-middle-a-test-pack-a.json");

        assertFileDoesNotExist(tempDir, "regulatory-reporting/config/test-pack-report-middle-a-test-pack-b.json");
        assertFileDoesNotExist(tempDir, "regulatory-reporting/config/test-pack-report-middle-a-test-pack-only-c.json");
    }


    @Test
    void writeSegregatedTestPacksWithFilter(@TempDir Path tempDir) throws Exception {
        // Trade Report Pipeline, startClass == TradeInstruction, middleClass == TradeReport, endClass == TradeProject
        PipelineTreeConfig tradeReportConf = new PipelineTreeConfig()
                .withTestPackIdFilter(startsWith("trade"))
                .starting(TransformType.ENRICH, helper.startClass())
                .add(helper.startClass(), TransformType.REPORT, helper.middleClass())
                .add(helper.middleClass(), TransformType.PROJECTION, helper.endClass())
                .strictUniqueIds().withWritePath(tempDir);

        Path enrichInputPath = Files.createDirectories(tempDir.resolve(TransformType.ENRICH.getResourcePath()).resolve("input"));

        Path tradeTestPack1Path = Files.createDirectories(enrichInputPath.resolve("trade").resolve("t-pack-1"));
        Files.write(tradeTestPack1Path.resolve("t-1-1.json"), "{\"name\": \"1-1t\"}".getBytes());
        Files.write(tradeTestPack1Path.resolve("t-1-2.json"), "{\"name\": \"1-2t\"}".getBytes());
        
        // Valuation Report Pipeline, middleBClass == ValuationReport. endBClass == ValuationProject
        PipelineTreeConfig valuationReportConf = new PipelineTreeConfig()
                .withTestPackIdFilter(startsWith("valuation"))
                .starting(TransformType.REPORT, helper.middleBClass())
                .add(helper.middleBClass(), TransformType.PROJECTION, helper.endBClass())
                .strictUniqueIds().withWritePath(tempDir);

        Path reportInputPath = Files.createDirectories(tempDir.resolve(TransformType.REPORT.getResourcePath()).resolve("input"));
        Path valuationTestPack1Path = Files.createDirectories(reportInputPath.resolve("valuation").resolve("v-pack-1"));

        Files.write(valuationTestPack1Path.resolve("v-1-1.json"), "{\"name\": \"1-1v\"}".getBytes());
        Files.write(valuationTestPack1Path.resolve("v-1-2.json"), "{\"name\": \"1-2v\"}".getBytes());
        
        pipelineTestPackWriter.writeTestPacks(tradeReportConf);
        pipelineTestPackWriter.writeTestPacks(valuationReportConf);
        
        assertFileExists(tempDir, "enrich/input/trade/t-pack-1/t-1-1.json");
        assertFileExists(tempDir, "enrich/input/trade/t-pack-1/t-1-2.json");

        assertFileExists(tempDir, "enrich/config/test-pack-enrich-start-trade-t-pack-1.json");
        assertFileExists(tempDir, "enrich/output/start/trade/t-pack-1/t-1-1.json");
        assertFileExists(tempDir, "enrich/output/start/trade/t-pack-1/t-1-2.json");

        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-start-middle-trade-t-pack-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle/trade/t-pack-1/t-1-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle/trade/t-pack-1/t-1-2.json");

        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-end-trade-t-pack-1.json");
        assertFileExists(tempDir, "projection/output/start/middle/end/trade/t-pack-1/t-1-1.json");
        assertFileExists(tempDir, "projection/output/start/middle/end/trade/t-pack-1/t-1-2.json");

        assertFileExists(tempDir, "regulatory-reporting/config/test-pack-report-middle-b-valuation-v-pack-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/middle-b/valuation/v-pack-1/v-1-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/middle-b/valuation/v-pack-1/v-1-2.json");
        
        assertFileExists(tempDir, "projection/config/test-pack-projection-middle-b-end-b-valuation-v-pack-1.json");
        assertFileExists(tempDir, "projection/output/middle-b/end-b/valuation/v-pack-1/v-1-1.json");
        assertFileExists(tempDir, "projection/output/middle-b/end-b/valuation/v-pack-1/v-1-2.json");
    }
    
    @Test
    void writeTestPacksForNestedTreeConfigNestedFolders(@TempDir Path tempDir) throws Exception {
        Path inputPath = Files.createDirectories(tempDir.resolve(TransformType.ENRICH.getResourcePath()).resolve("input"));

        Path testPack1Path = Files.createDirectories(inputPath.resolve("test-pack").resolve("1"));
        Path testPack2Path = Files.createDirectories(inputPath.resolve("test-pack").resolve("2"));

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
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-b/test-pack/2/sample-2-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-b/test-pack/2/sample-2-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-b/test-pack/1/sample-1-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-b/test-pack/1/sample-1-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-a/test-pack/2/sample-2-2.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-a/test-pack/2/sample-2-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-a/test-pack/1/sample-1-1.json");
        assertFileExists(tempDir, "regulatory-reporting/output/start/middle-a/test-pack/1/sample-1-2.json");
        assertFileExists(tempDir, "enrich/config/test-pack-enrich-start-test-pack-1.json");
        assertFileExists(tempDir, "enrich/config/test-pack-enrich-start-test-pack-2.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack/2/sample-2-2.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack/2/sample-2-1.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack/1/sample-1-1.json");
        assertFileExists(tempDir, "enrich/output/start/test-pack/1/sample-1-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-a-end-a-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-b-end-b-test-pack-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-a-end-b-test-pack-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-b-end-a-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-b-end-a-test-pack-2.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-a-end-b-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-b-end-b-test-pack-1.json");
        assertFileExists(tempDir, "projection/config/test-pack-projection-start-middle-a-end-a-test-pack-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-a/test-pack/2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-a/test-pack/2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-a/test-pack/1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-a/test-pack/1/sample-1-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-b/test-pack/2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-b/test-pack/2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-b/test-pack/1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-b/end-b/test-pack/1/sample-1-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-a/test-pack/2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-a/test-pack/2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-a/test-pack/1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-a/test-pack/1/sample-1-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-b/test-pack/2/sample-2-2.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-b/test-pack/2/sample-2-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-b/test-pack/1/sample-1-1.json");
        assertFileExists(tempDir, "projection/output/start/middle-a/end-b/test-pack/1/sample-1-2.json");
    }


    private static void assertFileExists(Path tempDir, String fileName) {
        assertTrue(Files.exists(tempDir.resolve(fileName)), String.format("File not found %s", fileName));
    }

    private static void assertFileDoesNotExist(Path tempDir, String fileName) {
        assertFalse(Files.exists(tempDir.resolve(fileName)), String.format("File found %s", fileName));
    }

    private void setupTestFiles(Path tempDir, TransformType transformType, String... testPacks) throws IOException, IOException {
        Path inputPath = Files.createDirectories(tempDir.resolve(transformType.getResourcePath()).resolve("input"));
        for (String testPack : testPacks) {
            Path testPackPath = Files.createDirectories(inputPath.resolve(testPack));
            Files.write(testPackPath.resolve("sample-1-1.json"), "{\"name\": \"1-1\"}".getBytes());
            Files.write(testPackPath.resolve("sample-1-2.json"), "{\"name\": \"1-2\"}".getBytes());
        }
    }

}
