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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.regnosys.rosetta.common.serialisation.TransformSerializationResolver;
import com.regnosys.rosetta.common.transform.FunctionNameHelper;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import com.regnosys.testing.serialisation.DefaultModelSerialisation;
import com.regnosys.testing.validation.ValidationSummariser;
import com.rosetta.model.lib.RosettaModelObject;
import org.slf4j.Logger;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import jakarta.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.rosetta.common.util.UrlUtils.getBaseFileName;
import static com.regnosys.rosetta.common.util.UrlUtils.toPortableString;

public class PipelineTestPackWriter {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PipelineTestPackWriter.class);

    static final String PARALLELISM_PROPERTY = "rune.testpack.parallelism";

    // Named, so they can be told apart in logs and thread dumps; daemons, so a stuck pool can't keep the JVM alive
    private static final ThreadFactory WORKER_THREADS = new ThreadFactoryBuilder().setNameFormat("testpack-worker-%d").setDaemon(true).build();

    // The default JSON mapper/writer for a transform side with no explicit format: the model's configured
    // defaultSerialisationFormat (rune-json or legacy), read from its rune-config.yml/rosetta-config.yml.
    private final DefaultModelSerialisation defaultSerialisation = DefaultModelSerialisation.resolve(this.getClass().getClassLoader());
    private final ObjectMapper defaultJsonObjectMapper = defaultSerialisation.getObjectMapper();

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

    /**
     * Generates the test packs of every node in the tree described by the config. For each node it runs the node's
     * function over every input sample that passes both the tree-wide filter and the node's own filter, writes each
     * output under the node's output path, and writes one test pack config per test pack.
     * <p>
     * Samples are generated on {@link #parallelism()} worker threads, one depth of the tree at a time: a node only reads
     * its upstream node's output, so the samples of every node at one depth can run together once the depth above has
     * finished. The same function instance is called from several threads, so model functions must be thread-safe.
     * The files written are the same as a sequential run's: samples in each test pack config are sorted by id, and
     * validation reports are added in input order.
     * <p>
     * Each node logs at INFO how many test packs it generated, how long its depth had been running when they were
     * written, and how many downstream functions read its output, as well as each test pack and sample it generates;
     * input and output paths are logged at DEBUG.
     *
     * @param config the tree to generate; does nothing (and logs an error) if it has no write path
     * @throws IOException if a sample can't be read or an output or config file can't be written
     */
    public void writeTestPacks(PipelineTreeConfig config) throws IOException {
        if (config.getWritePath() == null) {
            LOGGER.error("Write path not configured. Aborting.");
            return;
        }
        Stopwatch stopwatch = Stopwatch.createStarted();
        ValidationSummariser validationSummariser = config.getValidationSummariser();

        LOGGER.debug("Starting test pack Generation");
        ObjectWriter configObjectWriter = ObjectMapperGenerator.createWriterMapper().writerWithDefaultPrettyPrinter();
        ObjectWriter jsonObjectWriter = defaultSerialisation.createWriter(config.isSortJsonPropertiesAlphabetically());

        Path resourcesPath = config.getWritePath();

        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(config);

        createCsvSampleFiles(resourcesPath, config.getCsvTestPackSourceFiles());

        Map<String, Long> downstreamCounts = downstreamCounts(pipelineTree);
        int parallelism = parallelism();
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, WORKER_THREADS);
        try {
            for (List<PipelineNode> level : levels(pipelineTree)) {
                Stopwatch levelStopwatch = Stopwatch.createStarted();

                // Start every test pack of every node at this depth, so all their samples share the worker threads
                Map<PipelineNode, List<TestPackRun>> runsByNode = new LinkedHashMap<>();
                for (PipelineNode pipelineNode : level) {
                    TransformType transformType = pipelineNode.getTransformType();
                    String functionName = pipelineNode.getFunction().getName();
                    LOGGER.info("Generating {} test packs for {} ", transformType, functionName);

                    final PipelineTestPackFilter pipelineTestPackFilter = config.getTestPackFilter();
                    if (pipelineTestPackFilter != null && pipelineTestPackFilter.getExcludedFunctionsFromTestPackGeneration().contains(pipelineNode.getFunction())) {
                        LOGGER.info("Aborting {} Test Pack Generation for {} as this has been excluded from Test Pack generation", transformType, functionName);
                        continue;
                    }

                    Path inputPath = resourcesPath.resolve(pipelineNode.getInputPath(config.isStrictUniqueIds()));
                    PipelineNode upstream = pipelineNode.getUpstream();
                    if (upstream == null) {
                        LOGGER.debug("Input path {} ", inputPath);
                    } else {
                        LOGGER.debug("Input path {} (output of {} {})", inputPath, upstream.getTransformType(), upstream.getFunction().getName());
                    }

                    Path outputPath = resourcesPath.resolve(pipelineNode.getOutputPath(config.isStrictUniqueIds()));
                    LOGGER.debug("Output path {} ", outputPath);

                    List<Path> inputSamples = findAllSamples(inputPath);

                    Map<String, List<Path>> testPackToSamples =
                            filterAndGroupingByTestPackId(resourcesPath, inputPath, inputSamples, config.getTestPackIdFilter().and(pipelineNode.getTestPackIdFilter()), config.getCsvTestPackSourceFiles());

                    Map<String, List<Path>> filteredTestPackToSamples = Optional.ofNullable(pipelineTestPackFilter)
                            .map(t -> filterTestPacks(pipelineNode, pipelineTestPackFilter, testPackToSamples)).orElse(testPackToSamples);

                    List<TestPackRun> runs = new ArrayList<>();
                    if (!filteredTestPackToSamples.isEmpty()) {
                        // One pipeline model and function runner per node, shared by all its test packs and samples
                        PipelineModel pipeline = pipelineModelBuilder.build(pipelineNode, config);
                        PipelineFunctionRunner functionRunner = createFunctionRunner(pipeline, config, jsonObjectWriter);
                        for (String testPackId : filteredTestPackToSamples.keySet()) {
                            List<Path> inputSamplesForTestPack = filteredTestPackToSamples.get(testPackId);
                            runs.add(startTestPack(resourcesPath, inputPath, outputPath, testPackId, inputSamplesForTestPack, pipelineNode, pipeline, functionRunner, executor));
                        }
                    }
                    runsByNode.put(pipelineNode, runs);
                }

                // Then wait for them in the same order, and write each test pack's config
                for (Map.Entry<PipelineNode, List<TestPackRun>> nodeRuns : runsByNode.entrySet()) {
                    PipelineNode pipelineNode = nodeRuns.getKey();
                    TransformType transformType = pipelineNode.getTransformType();
                    for (TestPackRun run : nodeRuns.getValue()) {
                        TestPackModel testPackModel = run.finish(config, validationSummariser);

                        Path writePath = Files.createDirectories(resourcesPath.resolve(transformType.getResourcePath()).resolve("config"));
                        Path writeFile = writePath.resolve(testPackModel.getId() + ".json");
                        configObjectWriter.writeValue(writeFile.toFile(), testPackModel);
                    }
                    LOGGER.info("Generated {} {} test packs for {}, took {}; output read by {} downstream functions",
                            nodeRuns.getValue().size(), transformType, pipelineNode.getFunction().getName(), levelStopwatch, downstreamCounts.getOrDefault(pipelineNode.id(true), 0L));
                }
            }
        } finally {
            executor.shutdownNow();
        }

        if (validationSummariser != null) {
            validationSummariser.summerize();
        }

        LOGGER.info("Test pack generation complete, took {} ({} worker threads)", stopwatch, parallelism);
    }

    /**
     * Number of nodes that read each node's output, keyed by node id.
     */
    private static Map<String, Long> downstreamCounts(PipelineTree pipelineTree) {
        return pipelineTree.getNodeList().stream()
                .map(PipelineNode::getUpstream)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(upstream -> upstream.id(true), Collectors.counting()));
    }

    /**
     * Number of samples generated at once: the processors available to the JVM (which respects container CPU limits),
     * or the {@value #PARALLELISM_PROPERTY} system property, e.g. to stay below a fractional CPU quota.
     */
    static int parallelism() {
        Integer configured = Integer.getInteger(PARALLELISM_PROPERTY);
        return configured != null && configured > 0 ? configured : Runtime.getRuntime().availableProcessors();
    }

    /**
     * The tree's nodes grouped by depth, shallowest first, so each node comes after the node whose output it reads.
     */
    private static Collection<List<PipelineNode>> levels(PipelineTree pipelineTree) {
        return pipelineTree.getNodeList().stream()
                .collect(Collectors.groupingBy(PipelineTestPackWriter::depth, TreeMap::new, Collectors.toList()))
                .values();
    }

    /**
     * Number of nodes above this one in the tree: 0 for a starting node.
     */
    private static int depth(PipelineNode node) {
        return node.getUpstream() == null ? 0 : depth(node.getUpstream()) + 1;
    }

    /**
     * Every regular file under the input folder, or none if the folder doesn't exist (for example when an upstream
     * node generated no output).
     */
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

    /**
     * Creates the function runner for a node: it reads each input sample, runs the node's function, post-processes and
     * validates the result, and serialises it.
     */
    private PipelineFunctionRunner createFunctionRunner(PipelineModel pipeline, PipelineTreeConfig config, ObjectWriter jsonObjectWriter) {
        PipelineModel.Transform transform = pipeline.getTransform();
        Class<? extends RosettaModelObject> inputType = toClass(transform.getInputType());
        Class<? extends RosettaModelObject> functionType = toClass(transform.getFunction());
        Class<? extends RosettaModelObject> outputType = toClass(transform.getOutputType());
        // XSD validation
        Validator outputXsdValidator = Optional.ofNullable(config.getXmlSchemaMap())
                .map(sm -> getXsdValidator(outputType, sm))
                .orElse(null);

        return functionRunnerProvider.create(transform.getType(),
                inputType,
                functionType,
                pipeline.getInputSerialisation(),
                pipeline.getOutputSerialisation(),
                defaultJsonObjectMapper,
                jsonObjectWriter,
                outputXsdValidator);
    }

    /**
     * Queues one task per sample of a test pack on the executor, and returns them as a {@link TestPackRun}. Each task
     * runs the node's function on its sample and writes the output under {@code outputDir}, with the output format's
     * file extension.
     */
    private TestPackRun startTestPack(Path resourcesPath,
                                      Path inputPath,
                                      Path outputDir,
                                      String testPackId,
                                      List<Path> inputSamplesForTestPack,
                                      PipelineNode pipelineNode,
                                      PipelineModel pipeline,
                                      PipelineFunctionRunner functionRunner,
                                      ExecutorService executor) {
        LOGGER.info("Test pack sample generation started for {}", testPackId);
        TransformType transformType = pipelineNode.getTransformType();
        LOGGER.info("{} {} samples to be generated", inputSamplesForTestPack.size(), transformType);
        Class<? extends RosettaModelObject> functionType = toClass(pipeline.getTransform().getFunction());
        String functionName = pipelineNode.getFunction().getSimpleName();

        List<Future<GeneratedSample>> samples = new ArrayList<>();
        for (Path inputSample : inputSamplesForTestPack) {
            samples.add(executor.submit(() -> {
                LOGGER.info("Generating {} function {} test pack {} sample {}", transformType, functionName, testPackId, inputSample.getFileName());

                Path relativeOutputPath = resourcesPath.relativize(outputDir.resolve(resourcesPath.relativize(inputPath).relativize(inputSample)));
                Path outputPath = relativeOutputPath.getParent().resolve(Path.of(updateFileExtensionBasedOnOutputFormat(functionType, pipeline, relativeOutputPath.toFile().getName())));

                PipelineFunctionResult result = functionRunner.run(resourcesPath.resolve(inputSample));
                TestPackModel.SampleModel.Assertions assertions = result.getAssertions();

                String baseFileName = getBaseFileName(inputSample.toUri().toURL());
                String displayName = baseFileName.replace("-", " ");

                // Sample paths are stored in the test-pack model and resolved as classpath
                // resources, so they always use "/" regardless of the platform separator
                TestPackModel.SampleModel sampleModel = new TestPackModel.SampleModel(baseFileName.toLowerCase(), displayName, toPortableString(inputSample), toPortableString(outputPath), assertions);

                Files.createDirectories(resourcesPath.resolve(outputPath).getParent());
                Files.write(resourcesPath.resolve(outputPath), result.getSerialisedOutput().getBytes());

                return new GeneratedSample(sampleModel, result.getValidationReport());
            }));
        }
        return new TestPackRun(pipelineNode, pipeline, testPackId, functionName, samples);
    }

    /**
     * A test pack whose samples are queued on the worker threads.
     */
    private final class TestPackRun {

        private final PipelineNode pipelineNode;
        private final PipelineModel pipeline;
        private final String testPackId;
        private final String functionName;
        private final List<Future<GeneratedSample>> samples;
        private final Stopwatch stopwatch = Stopwatch.createStarted();

        private TestPackRun(PipelineNode pipelineNode, PipelineModel pipeline, String testPackId, String functionName, List<Future<GeneratedSample>> samples) {
            this.pipelineNode = pipelineNode;
            this.pipeline = pipeline;
            this.testPackId = testPackId;
            this.functionName = functionName;
            this.samples = samples;
        }

        /**
         * Waits for the samples in input order, adding each validation report to the summariser in that order, so a
         * summary doesn't depend on which sample finished first. Returns the test pack model, with its samples sorted
         * by id.
         *
         * @throws IOException if a sample failed with an I/O error
         */
        private TestPackModel finish(PipelineTreeConfig config, ValidationSummariser validationSummariser) throws IOException {
            List<TestPackModel.SampleModel> sampleModels = new ArrayList<>();
            for (Future<GeneratedSample> future : samples) {
                GeneratedSample sample = await(future);
                if (validationSummariser != null) {
                    validationSummariser.addValidationReport(pipeline, sample.sampleModel.getName(), sample.sampleModel, sample.validationReport);
                }
                sampleModels.add(sample.sampleModel);
            }
            sampleModels.sort(Comparator.comparing(TestPackModel.SampleModel::getId));

            LOGGER.info("Function {} test pack {} generation complete, took {}", functionName, testPackId, stopwatch);

            String pipelineId = pipelineNode.id(config.isStrictUniqueIds());
            String pipelineIdSuffix = pipelineNode.idSuffix(config.isStrictUniqueIds(), "-");
            String testPackName = helper.capitalizeFirstLetter(testPackId.replace("-", " "));
            return new TestPackModel(String.format("test-pack-%s-%s-%s", pipelineNode.getTransformType().name().toLowerCase(), pipelineIdSuffix, testPackId), pipelineId, testPackName, sampleModels);
        }
    }

    /**
     * A generated sample: its entry for the test pack config and its validation report.
     */
    private static final class GeneratedSample {

        private final TestPackModel.SampleModel sampleModel;
        private final ValidationReport validationReport;

        private GeneratedSample(TestPackModel.SampleModel sampleModel, ValidationReport validationReport) {
            this.sampleModel = sampleModel;
            this.validationReport = validationReport;
        }
    }

    /**
     * Waits for a task and returns its result, rethrowing the exception it failed with.
     */
    private static <T> T await(Future<T> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while generating test pack samples", e);
        } catch (ExecutionException e) {
            Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
            Throwables.throwIfUnchecked(e.getCause());
            throw new IllegalStateException(e.getCause());
        }
    }

    private String updateFileExtensionBasedOnOutputFormat(Class<?> functionType, PipelineModel pipelineModel, String fileName) {
        return fileName.substring(0, fileName.lastIndexOf(".")) + "." + outputFileExtension(functionType, pipelineModel.getOutputSerialisation());
    }

    /**
     * The function's {@code @Projection} annotation decides the output format, as it does for the output
     * writer; the pipeline's output serialisation applies only to functions without one.
     */
    @SuppressWarnings("deprecation")
    static String outputFileExtension(Class<?> functionType, PipelineModel.Serialisation outputSerialisation) {
        return TransformSerializationResolver.output(functionType, outputSerialisation)
                .map(s -> PipelineModel.Serialisation.Format.valueOf(s.getFormat().name()).getFileExtension())
                .orElse("json");
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

    private Map<String, List<Path>> filterTestPacks(PipelineNode pipelineNode, PipelineTestPackFilter pipelineTestPackFilter, Map<String, List<Path>> testPackToSamples) {
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
            return new SchemaValidator(schemaFactory.newSchema(schemaUrl));
        } catch (SAXException e) {
            throw new RuntimeException(String.format("Failed to create schema validator for %s", schemaUrl), e);
        }
    }

    /**
     * A {@link Validator} that validates each document with a new validator from the schema. A {@code Validator} isn't
     * thread-safe, so this is what lets one function runner be shared by the worker threads.
     */
    private static final class SchemaValidator extends Validator {

        private final Schema schema;
        private ErrorHandler errorHandler;
        private LSResourceResolver resourceResolver;

        private SchemaValidator(Schema schema) {
            this.schema = schema;
        }

        @Override
        public void validate(Source source, Result result) throws SAXException, IOException {
            Validator validator = schema.newValidator();
            validator.setErrorHandler(errorHandler);
            validator.setResourceResolver(resourceResolver);
            validator.validate(source, result);
        }

        @Override
        public void reset() {
        }

        @Override
        public void setErrorHandler(ErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return errorHandler;
        }

        @Override
        public void setResourceResolver(LSResourceResolver resourceResolver) {
            this.resourceResolver = resourceResolver;
        }

        @Override
        public LSResourceResolver getResourceResolver() {
            return resourceResolver;
        }
    }

    public boolean isSubPath(Path base, Path other) {
        Path basePath = base.normalize();
        Path otherPath = other.normalize();
        return otherPath.startsWith(basePath);
    }

}
