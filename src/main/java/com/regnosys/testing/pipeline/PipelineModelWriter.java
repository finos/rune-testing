package com.regnosys.testing.pipeline;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
            Path writePath = Files.createDirectories(resourcesPath.resolve(pipeline.getTransform().getType().getResourcePath()).resolve("config"));
            Path writeFile = writePath.resolve(pipeline.getId() + ".json");
            objectWriter.writeValue(writeFile.toFile(), pipeline);
        }
    }
}
