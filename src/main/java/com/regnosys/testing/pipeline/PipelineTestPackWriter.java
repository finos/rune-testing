package com.regnosys.testing.pipeline;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PipelineTestPackWriter {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PipelineTestPackWriter.class);
    private final PipelineTreeBuilder pipelineTreeBuilder;
    private final PipelineModelBuilder pipelineModelBuilder;
    private final PipelineFunctionRunner pipelineFunctionRunner;

    private final FunctionNameHelper helper;


    @Inject
    public PipelineTestPackWriter(PipelineTreeBuilder pipelineTreeBuilder, PipelineFunctionRunner pipelineFunctionRunner, PipelineModelBuilder pipelineModelBuilder, FunctionNameHelper helper) {
        this.pipelineTreeBuilder = pipelineTreeBuilder;
        this.pipelineFunctionRunner = pipelineFunctionRunner;
        this.pipelineModelBuilder = pipelineModelBuilder;
        this.helper = helper;
    }

    void writeTestPacks(PipelineTreeConfig config) throws IOException {
        if (config.getWritePath() == null) {
            LOGGER.error("Write path not configured. Aborting.");
            return;
        }
        ObjectWriter objectWriter = ObjectMapperGenerator.createWriterMapper().writerWithDefaultPrettyPrinter();

        Path resourcesPath = config.getWritePath();

        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(config);
        for (PipelineNode pipelineNode : pipelineTree.getNodeList()) {
            Path inputPath = resourcesPath.resolve(pipelineNode.getInputPath(config.isStrictUniqueIds()));
            Path outputPath = resourcesPath.resolve(pipelineNode.getOutputPath(config.isStrictUniqueIds()));
            List<Path> inputSamples = inputSamples(inputPath);

            Map<String, List<Path>> testPackToSamples = groupingByTestPackId(resourcesPath, inputPath, inputSamples);
            for (String testPackId : testPackToSamples.keySet()) {
                List<Path> inputSamplesForTestPack = testPackToSamples.get(testPackId);
                TestPackModel testPackModel = writeTestPackSamples(resourcesPath, inputPath, outputPath, testPackId, inputSamplesForTestPack, pipelineNode, config);

                Path writePath = Files.createDirectories(resourcesPath.resolve(pipelineNode.getTransformType().getResourcePath()).resolve("config"));
                Path writeFile = writePath.resolve(testPackModel.getId() + ".json");
                objectWriter.writeValue(writeFile.toFile(), testPackModel);
            }
        }
    }

    private List<Path> inputSamples(Path inputDir) throws IOException {
        try (Stream<Path> paths = Files.walk(inputDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(Files::exists)
                    .collect(Collectors.toList());
        }
    }

    private TestPackModel writeTestPackSamples(Path resourcesPath, Path inputPath, Path outputDir, String testPackId, List<Path> inputSamplesForTestPack, PipelineNode pipelineNode, PipelineTreeConfig config) throws IOException {
        List<TestPackModel.SampleModel> sampleModels = new ArrayList<>();
        String pipelineId = pipelineNode.id(config.isStrictUniqueIds());
        String pipelineIdSuffix = pipelineNode.idSuffix(config.isStrictUniqueIds(), "-");

        for (Path inputSample : inputSamplesForTestPack) {
            Path outputSample = resourcesPath.relativize(outputDir.resolve(resourcesPath.relativize(inputPath).relativize(inputSample)));
            PipelineModel pipeline = pipelineModelBuilder.build(pipelineNode, config);
            PipelineFunctionRunner.Result run = pipelineFunctionRunner.run(pipeline, config.getXmlSchemaMap(), resourcesPath.resolve(inputSample));
            TestPackModel.SampleModel.Assertions assertions = run.getAssertions();

            String sampleId = inputSample.getFileName().toString().toLowerCase();
            TestPackModel.SampleModel sampleModel = new TestPackModel.SampleModel(sampleId, inputSample.getFileName().toString(), inputSample.toString(), outputSample.toString(), assertions);
            sampleModels.add(sampleModel);

            Files.createDirectories(resourcesPath.resolve(outputSample).getParent());
            Files.write(resourcesPath.resolve(outputSample), run.getSerialisedOutput().getBytes());
        }
        String testPackName = testPackId.replace("-", " ");
        return new TestPackModel(String.format("test-pack-%s-%s-%s", pipelineNode.getTransformType().name().toLowerCase(), pipelineIdSuffix, testPackId), pipelineId, testPackName, sampleModels);
    }

    private Map<String, List<Path>> groupingByTestPackId(Path resourcesPath, Path inputPath, List<Path> inputSamples) {
        return inputSamples.stream()
                .map(resourcesPath::relativize)
                .collect(Collectors.groupingBy(p -> testPackId(resourcesPath, inputPath, p)));
    }

    private String testPackId(Path resourcesPath, Path inputPath, Path samplePath) {
        Path parent = samplePath.getParent();
        Path relativePath = resourcesPath.relativize(inputPath).relativize(parent);
        return relativePath.toString().replace(File.pathSeparatorChar, '-');
    }
}
