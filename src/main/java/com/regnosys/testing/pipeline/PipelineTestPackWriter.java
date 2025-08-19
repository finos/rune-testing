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

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.transform.FunctionNameHelper;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import com.regnosys.testing.validation.ValidationSummariser;
import com.rosetta.model.lib.RosettaModelObject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.xml.sax.SAXException;

import jakarta.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.rosetta.common.util.UrlUtils.getBaseFileName;

public class PipelineTestPackWriter {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PipelineTestPackWriter.class);

    private static final ObjectMapper JSON_OBJECT_MAPPER = RosettaObjectMapper.getNewRosettaObjectMapper();

    private final PipelineTreeBuilder pipelineTreeBuilder;
    private final PipelineModelBuilder pipelineModelBuilder;
    private final PipelineFunctionRunnerProvider functionRunnerProvider;
    private final FunctionNameHelper helper;


    @Inject
    public PipelineTestPackWriter(PipelineTreeBuilder pipelineTreeBuilder, PipelineFunctionRunnerProvider functionRunnerProvider, PipelineModelBuilder pipelineModelBuilder, FunctionNameHelper helper) {
        this.pipelineTreeBuilder = pipelineTreeBuilder;
        this.functionRunnerProvider = functionRunnerProvider;
        this.pipelineModelBuilder = pipelineModelBuilder;
        this.helper = helper;
    }

    public void writeTestPacks(PipelineTreeConfig config) throws IOException {
        if (config.getWritePath() == null) {
            LOGGER.error("Write path not configured. Aborting.");
            return;
        }
        Stopwatch stopwatch = Stopwatch.createStarted();
        ValidationSummariser validationSummariser = config.getValidationSummariser();

        LOGGER.info("Starting test pack Generation");
        ObjectWriter configObjectWriter = ObjectMapperGenerator.createWriterMapper().writerWithDefaultPrettyPrinter();
        ObjectWriter jsonObjectWriter =
                JSON_OBJECT_MAPPER
                        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, config.isSortJsonPropertiesAlphabetically())
                        .writerWithDefaultPrettyPrinter();

        Path resourcesPath = config.getWritePath();

        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(config);

        createCsvSampleFiles(resourcesPath, config.getCsvTestPackSourceFiles());

        for (PipelineNode pipelineNode : pipelineTree.getNodeList()) {
            Stopwatch pipelineStopwatch = Stopwatch.createStarted();
            TransformType transformType = pipelineNode.getTransformType();
            String functionName = pipelineNode.getFunction().getName();
            LOGGER.info("Generating {} test packs for {} ", transformType, functionName);

            final PipelineTestPackFilter pipelineTestPackFilter = config.getTestPackFilter();
            if (pipelineTestPackFilter != null && pipelineTestPackFilter.getExcludedFunctionsFromTestPackGeneration().contains(pipelineNode.getFunction())) {
                LOGGER.info("Aborting {} Test Pack Generation for {} as this has been excluded from Test Pack generation", transformType, functionName);
                continue;
            }

            Path inputPath = resourcesPath.resolve(pipelineNode.getInputPath(config.isStrictUniqueIds()));
            LOGGER.info("Input path {} ", inputPath);

            Path outputPath = resourcesPath.resolve(pipelineNode.getOutputPath(config.isStrictUniqueIds()));
            LOGGER.info("Output path {} ", outputPath);

            List<Path> inputSamples = findAllSamples(inputPath);

            Map<String, List<Path>> testPackToSamples =
                    filterAndGroupingByTestPackId(resourcesPath, inputPath, inputSamples, config.getTestPackIdFilter(), config.getCsvTestPackSourceFiles());

            Map<String, List<Path>> filteredTestPackToSamples = Optional.ofNullable(pipelineTestPackFilter)
                    .map(t -> filterTestPacks(pipelineNode, pipelineTestPackFilter, testPackToSamples)).orElse(testPackToSamples);

            for (String testPackId : filteredTestPackToSamples.keySet()) {
                List<Path> inputSamplesForTestPack = filteredTestPackToSamples.get(testPackId);
                TestPackModel testPackModel = writeTestPackSamples(resourcesPath, inputPath, outputPath, testPackId, inputSamplesForTestPack, pipelineNode, config, jsonObjectWriter, validationSummariser);

                Path writePath = Files.createDirectories(resourcesPath.resolve(transformType.getResourcePath()).resolve("config"));
                Path writeFile = writePath.resolve(testPackModel.getId() + ".json");
                configObjectWriter.writeValue(writeFile.toFile(), testPackModel);
            }
            LOGGER.info("Generated {} {} test packs for {}, took {}", filteredTestPackToSamples.size(), transformType, functionName, pipelineStopwatch);
        }

        if (validationSummariser != null) {
            validationSummariser.summerize();
        }

        LOGGER.info("Test pack generation complete, took {}", stopwatch);
    }

    private List<Path> findAllSamples(Path inputDir) throws IOException {
        if (!Files.exists(inputDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(inputDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(Files::exists)
                    .collect(Collectors.toList());
        }
    }

    private TestPackModel writeTestPackSamples(Path resourcesPath,
                                               Path inputPath,
                                               Path outputDir,
                                               String testPackId,
                                               List<Path> inputSamplesForTestPack,
                                               PipelineNode pipelineNode,
                                               PipelineTreeConfig config,
                                               ObjectWriter jsonObjectWriter,
                                               ValidationSummariser validationSummariser) throws IOException {
        LOGGER.info("Test pack sample generation started for {}", testPackId);
        TransformType transformType = pipelineNode.getTransformType();
        LOGGER.info("{} {} samples to be generated", inputSamplesForTestPack.size(), transformType);
        List<TestPackModel.SampleModel> sampleModels = new ArrayList<>();
        String pipelineId = pipelineNode.id(config.isStrictUniqueIds());
        String pipelineIdSuffix = pipelineNode.idSuffix(config.isStrictUniqueIds(), "-");
        PipelineModel pipeline = pipelineModelBuilder.build(pipelineNode, config);

        PipelineModel.Transform transform = pipeline.getTransform();
        Class<? extends RosettaModelObject> inputType = toClass(transform.getInputType());
        Class<? extends RosettaModelObject> functionType = toClass(transform.getFunction());
        Class<? extends RosettaModelObject> outputType = toClass(transform.getOutputType());
        // XSD validation
        Validator outputXsdValidator = Optional.ofNullable(config.getXmlSchemaMap())
                .map(sm -> getXsdValidator(outputType, sm))
                .orElse(null);

        PipelineFunctionRunner functionRunner =
                functionRunnerProvider.create(transform.getType(),
                        inputType,
                        functionType,
                        pipeline.getInputSerialisation(),
                        pipeline.getOutputSerialisation(),
                        JSON_OBJECT_MAPPER,
                        jsonObjectWriter,
                        outputXsdValidator);

        String functionName = functionType.getSimpleName();
        Stopwatch stopwatch = Stopwatch.createStarted();
        for (Path inputSample : inputSamplesForTestPack) {
            LOGGER.info("Generating {} function {} test pack {} sample {}", transformType, functionName, testPackId, inputSample.getFileName());

            Path relativeOutputPath = resourcesPath.relativize(outputDir.resolve(resourcesPath.relativize(inputPath).relativize(inputSample)));
            Path outputPath = relativeOutputPath.getParent().resolve(Path.of(updateFileExtensionBasedOnOutputFormat(pipeline, relativeOutputPath.toFile().getName())));

            PipelineFunctionResult result = functionRunner.run(resourcesPath.resolve(inputSample));
            TestPackModel.SampleModel.Assertions assertions = result.getAssertions();

            String baseFileName = getBaseFileName(inputSample.toUri().toURL());
            String displayName = baseFileName.replace("-", " ");

            TestPackModel.SampleModel sampleModel = new TestPackModel.SampleModel(baseFileName.toLowerCase(), displayName, inputSample.toString(), outputPath.toString(), assertions);
            sampleModels.add(sampleModel);

            Files.createDirectories(resourcesPath.resolve(outputPath).getParent());
            Files.write(resourcesPath.resolve(outputPath), result.getSerialisedOutput().getBytes());

            ValidationReport validationReport = result.getValidationReport();
            if (validationSummariser != null) {
                validationSummariser.addValidationReport(pipeline, sampleModel.getName(), sampleModel, validationReport);
            }
        }

        List<TestPackModel.SampleModel> sortedSamples = sampleModels
                .stream()
                .sorted(Comparator.comparing(TestPackModel.SampleModel::getId))
                .collect(Collectors.toList());

        LOGGER.info("Function {} test pack {} generation complete, took {}", functionName, testPackId, stopwatch);

        String testPackName = helper.capitalizeFirstLetter(testPackId.replace("-", " "));
        return new TestPackModel(String.format("test-pack-%s-%s-%s", transformType.name().toLowerCase(), pipelineIdSuffix, testPackId), pipelineId, testPackName, sortedSamples);
    }

    @NotNull
    private String updateFileExtensionBasedOnOutputFormat(PipelineModel pipelineModel, String fileName) {
        String outputFormat = Optional.ofNullable(pipelineModel.getOutputSerialisation())
                .map(PipelineModel.Serialisation::getFormat)
                .map(PipelineModel.Serialisation.Format::toString)
                .orElse("json")
                .toLowerCase();
        return fileName.substring(0, fileName.lastIndexOf(".")) + "." + outputFormat;
    }

    private void createCsvSampleFiles(Path resourcePath, ImmutableSet<Path> csvTestPackSourceFiles) throws IOException {
        for (Path csvSourceFile : csvTestPackSourceFiles) {
            Path resolvedCsvSourcePath = resourcePath.resolve(csvSourceFile);
            try (BufferedReader reader = Files.newBufferedReader(resolvedCsvSourcePath)) {
                String header = reader.readLine();
                if (header == null) {
                    throw new IOException("CSV file is empty: " + resolvedCsvSourcePath);
                }

                String line;
                int rowNum = 1;

                String baseName = com.google.common.io.Files.getNameWithoutExtension(resolvedCsvSourcePath.toString());
                String extension = com.google.common.io.Files.getFileExtension(resolvedCsvSourcePath.toString());

                while ((line = reader.readLine()) != null) {
                    String fileName = String.format("%s_%d.%s", baseName, rowNum++, extension);
                    Path outFile = resolvedCsvSourcePath.getParent().resolve(fileName);

                    try (BufferedWriter writer = Files.newBufferedWriter(outFile)) {
                        writer.write(header);
                        writer.newLine();
                        writer.write(line);
                    }
                }
            }
        }
    }

    private Map<String, List<Path>> filterAndGroupingByTestPackId(Path resourcesPath, Path inputPath, List<Path> inputSamples, Predicate<String> testPackIdFilter, ImmutableSet<Path> csvTestPackSourceFiles) {
        return inputSamples.stream()
                .map(resourcesPath::relativize)
                .filter(path -> testPackIdFilter.test(testPackId(resourcesPath, inputPath, path)))
                .filter(path -> !csvTestPackSourceFiles.contains(path))
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

    @SuppressWarnings("unchecked")
    private Class<? extends RosettaModelObject> toClass(String name) {
        try {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                return (Class<? extends RosettaModelObject>) contextClassLoader.loadClass(name);
            }
            return (Class<? extends RosettaModelObject>) Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Validator getXsdValidator(Class<?> functionType, ImmutableMap<Class<?>, String> outputSchemaMap) {
        URL schemaUrl = Optional.ofNullable(outputSchemaMap.get(functionType))
                .map(Resources::getResource)
                .orElse(null);
        if (schemaUrl == null) {
            return null;
        }
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // required to process xml elements with an maxOccurs greater than 5000 (rather than unbounded)
            schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            Schema schema = schemaFactory.newSchema(schemaUrl);
            return schema.newValidator();
        } catch (SAXException e) {
            throw new RuntimeException(String.format("Failed to create schema validator for %s", schemaUrl), e);
        }
    }

    public boolean isSubPath(Path base, Path other) {
        Path basePath = base.normalize();
        Path otherPath = other.normalize();
        return otherPath.startsWith(basePath);
    }

}
