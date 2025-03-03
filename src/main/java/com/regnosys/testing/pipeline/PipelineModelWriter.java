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

import com.fasterxml.jackson.databind.ObjectWriter;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import org.slf4j.Logger;

import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PipelineModelWriter {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PipelineModelWriter.class);
    private final PipelineTreeBuilder pipelineTreeBuilder;
    private final PipelineModelBuilder pipelineModelBuilder;

    @Inject
    public PipelineModelWriter(PipelineTreeBuilder pipelineTreeBuilder, PipelineModelBuilder pipelineModelBuilder) {
        this.pipelineTreeBuilder = pipelineTreeBuilder;
        this.pipelineModelBuilder = pipelineModelBuilder;
    }

    public void writePipelines(PipelineTreeConfig config) throws IOException {
        if (config.getWritePath() == null) {
            LOGGER.error("Write path not configured. Aborting.");
            return;
        }

        Path resourcesPath = config.getWritePath();

        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(config);
        List<PipelineModel> allPipelines = pipelineModelBuilder.createPipelineModels(pipelineTree);
        ObjectWriter objectWriter = ObjectMapperGenerator.createWriterMapper().writerWithDefaultPrettyPrinter();
        for (PipelineModel pipeline : allPipelines) {
            LOGGER.info("Generating {} pipeline config files for {}", pipeline.getTransform().getType(), pipeline.getName());
            Path writePath = Files.createDirectories(resourcesPath.resolve(pipeline.getTransform().getType().getResourcePath()).resolve("config"));
            Path writeFile = writePath.resolve(pipeline.getId() + ".json");
            objectWriter.writeValue(writeFile.toFile(), pipeline);
        }
    }
}
