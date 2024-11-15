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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.rosetta.common.util.UrlUtils.getBaseFileName;

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

    public void writeTestPacks(PipelineTreeConfig config) throws IOException {
        if (config.getWritePath() == null) {
            LOGGER.error("Write path not configured. Aborting.");
            return;
        }
        LOGGER.info("Starting Test Pack Generation");
        ObjectWriter objectWriter = ObjectMapperGenerator.createWriterMapper().writerWithDefaultPrettyPrinter();

        Path resourcesPath = config.getWritePath();

        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(config);

        for (PipelineNode pipelineNode : pipelineTree.getNodeList()) {
            LOGGER.info("Generating {} Test Packs for {} ", pipelineNode.getTransformType(), pipelineNode.getFunction().getName());

            final PipelineTestPackFilter pipelineTestPackFilter = config.getTestPackFilter();
            if (pipelineTestPackFilter != null && pipelineTestPackFilter.getExcludedFunctionsFromTestPackGeneration().contains(pipelineNode.getFunction())) {
                LOGGER.info("Aborting {} Test Pack Generation for {} as this has been excluded from Test Pack generation", pipelineNode.getTransformType(), pipelineNode.getFunction().getName());
                continue;
            }

            Path inputPath = resourcesPath.resolve(pipelineNode.getInputPath(config.isStrictUniqueIds()));
            LOGGER.info("Input path {} ", inputPath);

            Path outputPath = resourcesPath.resolve(pipelineNode.getOutputPath(config.isStrictUniqueIds()));
            LOGGER.info("Output path {} ", outputPath);

            List<Path> inputSamples = findAllJsonSamples(inputPath);

            Map<String, List<Path>> testPackToSamples =
                    filterAndGroupingByTestPackId(resourcesPath, inputPath, inputSamples, config.getTestPackIdInclusionFilter());

            Map<String, List<Path>> filteredTestPackToSamples = Optional.ofNullable(pipelineTestPackFilter)
                    .map(t -> filterTestPacks(pipelineNode, pipelineTestPackFilter, testPackToSamples)).orElse(testPackToSamples);

            LOGGER.info("{} Test Packs will be generated", filteredTestPackToSamples.keySet().size());

            for (String testPackId : filteredTestPackToSamples.keySet()) {
                List<Path> inputSamplesForTestPack = filteredTestPackToSamples.get(testPackId);
                TestPackModel testPackModel = writeTestPackSamples(resourcesPath, inputPath, outputPath, testPackId, inputSamplesForTestPack, pipelineNode, config);

                Path writePath = Files.createDirectories(resourcesPath.resolve(pipelineNode.getTransformType().getResourcePath()).resolve("config"));
                Path writeFile = writePath.resolve(testPackModel.getId() + ".xml");
                objectWriter.writeValue(writeFile.toFile(), testPackModel);
            }
        }
    }

    private List<Path> findAllJsonSamples(Path inputDir) throws IOException {
        if (!Files.exists(inputDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(inputDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(Files::exists)
                    .filter(x -> x.getFileName().toString().endsWith(".xml"))
                    .collect(Collectors.toList());
        }
    }

    private TestPackModel writeTestPackSamples(Path resourcesPath, Path inputPath, Path outputDir, String testPackId, List<Path> inputSamplesForTestPack, PipelineNode pipelineNode, PipelineTreeConfig config) throws IOException {
        LOGGER.info("Test pack sample generation started for {}", testPackId);
        TransformType transformType = pipelineNode.getTransformType();
        LOGGER.info("{} {} samples to be generated", inputSamplesForTestPack.size(), transformType);
        List<TestPackModel.SampleModel> sampleModels = new ArrayList<>();
        String pipelineId = pipelineNode.id(config.isStrictUniqueIds());
        String pipelineIdSuffix = pipelineNode.idSuffix(config.isStrictUniqueIds(), "-");

        for (Path inputSample : inputSamplesForTestPack) {
            LOGGER.info("Generating {} sample {}", transformType, inputSample.getFileName());

            PipelineModel pipeline = pipelineModelBuilder.build(pipelineNode, config);
            Path outputSample = resourcesPath.relativize(outputDir.resolve(resourcesPath.relativize(inputPath).relativize(inputSample)));
            outputSample = outputSample.getParent().resolve(Path.of(updateFileExtensionBasedOnOutputFormat(pipeline, outputSample.toFile().getName())));

            PipelineFunctionRunner.Result run = pipelineFunctionRunner.run(pipeline, config.getXmlSchemaMap(), resourcesPath.resolve(inputSample));
            TestPackModel.SampleModel.Assertions assertions = run.getAssertions();

            String baseFileName = getBaseFileName(inputSample.toUri().toURL());
            String displayName = baseFileName.replace("-", " ");

            TestPackModel.SampleModel sampleModel = new TestPackModel.SampleModel(baseFileName.toLowerCase(), displayName, inputSample.toString(), outputSample.toString(), assertions);
            sampleModels.add(sampleModel);

            Files.createDirectories(resourcesPath.resolve(outputSample).getParent());
            Files.write(resourcesPath.resolve(outputSample), run.getSerialisedOutput().getBytes());
        }

        List<TestPackModel.SampleModel> sortedSamples = sampleModels
                .stream()
                .sorted(Comparator.comparing(TestPackModel.SampleModel::getId))
                .collect(Collectors.toList());

        LOGGER.info("Test Pack sample generation complete for {} ", testPackId);

        String testPackName = helper.capitalizeFirstLetter(testPackId.replace("-", " "));
        return new TestPackModel(String.format("test-pack-%s-%s-%s", transformType.name().toLowerCase(), pipelineIdSuffix, testPackId), pipelineId, testPackName, sortedSamples);
    }

    private String updateFileExtensionBasedOnOutputFormat(PipelineModel pipelineModel, String fileName) {
        if (pipelineModel.getOutputSerialisation() != null) {
            String outputFormat = pipelineModel.getOutputSerialisation().getFormat().toString().toLowerCase();
            return fileName.substring(0, fileName.lastIndexOf(".")) + "." + outputFormat;
        }
        return fileName;
    }

    private Map<String, List<Path>> filterAndGroupingByTestPackId(Path resourcesPath, Path inputPath, List<Path> inputSamples, Predicate<String> testPackSampleInclusionFilter) {
        return inputSamples.stream()
                .map(resourcesPath::relativize)
                .filter(path -> testPackSampleInclusionFilter.test(testPackId(resourcesPath, inputPath, path)))
                .collect(Collectors.groupingBy(p -> testPackId(resourcesPath, inputPath, p)));
    }

    private String testPackId(Path resourcesPath, Path inputPath, Path samplePath) {
        Path parent = samplePath.getParent();
        Path relativePath = resourcesPath.relativize(inputPath).relativize(parent);
        return relativePath.toString().replace(File.separatorChar, '-');
    }

    private @NotNull Map<String, List<Path>> filterTestPacks(PipelineNode pipelineNode, PipelineTestPackFilter pipelineTestPackFilter, Map<String, List<Path>> testPackToSamples) {
        Map<String, List<Path>> filteredTestPackToSamples = testPackToSamples;
        final Set<String> testPackSpecificFunctions = pipelineTestPackFilter.getTestPacksSpecificToFunctions().entries()
                .stream().filter(entry -> entry.getValue() == pipelineNode.getFunction()).map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (pipelineTestPackFilter.getTestPacksSpecificToFunctions().containsValue(pipelineNode.getFunction())) {
            filteredTestPackToSamples = testPackToSamples.entrySet().stream()
                    .filter(entry -> testPackSpecificFunctions.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else {
            final ImmutableCollection<String> testPacksToRemove = pipelineTestPackFilter.getTestPacksSpecificToFunctions().keys();
            // Filter out the test packs that are not valid for this function
            filteredTestPackToSamples = filteredTestPackToSamples.entrySet().stream()
                    .filter(entry -> !testPacksToRemove.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        // Check if the function has specific test packs
        if (pipelineTestPackFilter.getFunctionsSpecificToTestPacks().containsKey(pipelineNode.getFunction())) {
            // Filter to include only the specific test packs for the function
            filteredTestPackToSamples = filteredTestPackToSamples.entrySet().stream()
                    .filter(entry -> pipelineTestPackFilter.getFunctionsSpecificToTestPacks()
                            .get(pipelineNode.getFunction())
                            .contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        if (pipelineTestPackFilter.getTestPacksRestrictedForFunctions().values().contains(pipelineNode.getFunction())) {
            // Filter to include only applicable test packs for this function
            filteredTestPackToSamples = filteredTestPackToSamples.entrySet().stream()
                    .filter(entry -> filterApplicableFunctionsForTestPack(entry.getKey(), pipelineNode, pipelineTestPackFilter.getTestPacksRestrictedForFunctions()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else {
            final ImmutableSet<String> testPacksRestrictedForFunctions = pipelineTestPackFilter.getTestPacksRestrictedForFunctions().keySet();
            // Filter out the test packs if not needed for this function
            filteredTestPackToSamples = filteredTestPackToSamples.entrySet().stream()
                    .filter(entry -> !testPacksRestrictedForFunctions.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return filteredTestPackToSamples;
    }

    protected boolean filterApplicableFunctionsForTestPack(String testPackName, PipelineNode pipelineNode, ImmutableMultimap<String, Class<?>> testPackIncludedReportIds) {
        ImmutableCollection<Class<?>> applicableReportsForTestPack = testPackIncludedReportIds.get(testPackName);
        return applicableReportsForTestPack.isEmpty() || applicableReportsForTestPack.contains(pipelineNode.getFunction());
    }
}
