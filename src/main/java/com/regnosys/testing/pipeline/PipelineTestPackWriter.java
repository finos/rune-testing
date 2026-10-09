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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.regnosys.rosetta.common.transform.FunctionNameHelper;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import com.regnosys.testing.serialisation.DefaultModelSerialisation;
import com.regnosys.testing.validation.ValidationSummariser;
import com.rosetta.model.lib.RosettaModelObject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.xml.sax.SAXException;

import jakarta.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.rosetta.common.util.UrlUtils.getBaseFileName;
import static com.regnosys.rosetta.common.util.UrlUtils.toPortableString;

public class PipelineTestPackWriter {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PipelineTestPackWriter.class);

    static final String PARALLELISM_PROPERTY = "rune.testpack.parallelism";

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
     * Samples run concurrently on a pool of {@link #parallelism()} worker threads. Nodes are taken one depth of the tree
     * at a time, since a node only reads its upstream node's output: every sample of every node at one depth is queued
     * on the pool, and the next depth starts when they have all finished. The same function instance is called from
     * several threads, so model functions must be thread-safe. Output is the same as a sequential run: samples in each
     * test pack config are sorted by id.
     * <p>
     * Each node logs one INFO line with how many test packs it generated, how long its samples took from the start of
     * its depth, and how many downstream functions read its output; paths and per-sample detail are logged at DEBUG.
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

        int parallelism = parallelism();
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, new TestPackWorkerThreadFactory());
        try {
            // A node only reads its upstream's output, so every sample of every node at one depth can run at once
            for (List<PipelineNode> level : levels(pipelineTree)) {
                writeLevel(level, pipelineTree, config, resourcesPath, configObjectWriter, jsonObjectWriter, validationSummariser, executor);
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
     * Number of samples generated at once: the processors available to the JVM (which respects container CPU limits),
     * or the {@value #PARALLELISM_PROPERTY} system property, e.g. to stay below a fractional CPU quota.
     */
    static int parallelism() {
        Integer configured = Integer.getInteger(PARALLELISM_PROPERTY);
        return configured != null && configured > 0 ? configured : Runtime.getRuntime().availableProcessors();
    }

    /**
     * Nodes grouped by depth from their starting node, shallowest first. Every node's upstream is in an earlier group.
     */
    static Collection<List<PipelineNode>> levels(PipelineTree pipelineTree) {
        Map<Integer, List<PipelineNode>> levels = new TreeMap<>();
        for (PipelineNode node : pipelineTree.getNodeList()) {
            levels.computeIfAbsent(depth(node), d -> new ArrayList<>()).add(node);
        }
        return levels.values();
    }

    /**
     * Number of upstream nodes between this node and its starting node: 0 for a starting node.
     */
    private static int depth(PipelineNode node) {
        int depth = 0;
        for (PipelineNode upstream = node.getUpstream(); upstream != null; upstream = upstream.getUpstream()) {
            depth++;
        }
        return depth;
    }

    /**
     * Generates every node at one depth of the tree. Each node is prepared in turn and its samples are queued on the
     * executor as soon as it is ready, so workers start while later nodes are still being prepared. Once every sample of
     * the level has finished, writes each node's test pack configs (samples sorted by id) and logs one line per node and
     * one for the level.
     *
     * @throws IOException if a sample fails with an I/O error, or a config can't be written
     */
    private void writeLevel(List<PipelineNode> level,
                            PipelineTree pipelineTree,
                            PipelineTreeConfig config,
                            Path resourcesPath,
                            ObjectWriter configObjectWriter,
                            ObjectWriter jsonObjectWriter,
                            ValidationSummariser validationSummariser,
                            ExecutorService executor) throws IOException {
        Stopwatch levelStopwatch = Stopwatch.createStarted();
        List<NodeWork> nodes = new ArrayList<>();
        int samples = 0;
        for (PipelineNode pipelineNode : level) {
            // Queue each node's samples as soon as it is prepared, so workers start while later nodes are prepared
            NodeWork node = prepareNode(pipelineNode, config, resourcesPath, jsonObjectWriter);
            if (node == null) {
                continue;
            }
            nodes.add(node);
            for (Map.Entry<String, List<Path>> testPack : node.testPackToSamples.entrySet()) {
                List<Future<TestPackModel.SampleModel>> futures = new ArrayList<>();
                for (Path inputSample : testPack.getValue()) {
                    futures.add(executor.submit(() -> {
                        TestPackModel.SampleModel sampleModel = generateSample(resourcesPath, node, testPack.getKey(), inputSample, validationSummariser);
                        node.lastSampleFinished.accumulateAndGet(levelStopwatch.elapsed(TimeUnit.NANOSECONDS), Math::max);
                        return sampleModel;
                    }));
                }
                node.sampleFutures.put(testPack.getKey(), futures);
                samples += futures.size();
            }
        }

        for (NodeWork node : nodes) {
            TransformType transformType = node.pipelineNode.getTransformType();
            for (Map.Entry<String, List<Future<TestPackModel.SampleModel>>> testPack : node.sampleFutures.entrySet()) {
                String testPackId = testPack.getKey();
                List<TestPackModel.SampleModel> sortedSamples = new ArrayList<>();
                for (Future<TestPackModel.SampleModel> future : testPack.getValue()) {
                    sortedSamples.add(await(future));
                }
                sortedSamples.sort(Comparator.comparing(TestPackModel.SampleModel::getId));

                String testPackName = helper.capitalizeFirstLetter(testPackId.replace("-", " "));
                TestPackModel testPackModel = new TestPackModel(String.format("test-pack-%s-%s-%s", transformType.name().toLowerCase(), node.pipelineIdSuffix, testPackId), node.pipelineId, testPackName, sortedSamples);

                Path writePath = Files.createDirectories(resourcesPath.resolve(transformType.getResourcePath()).resolve("config"));
                Path writeFile = writePath.resolve(testPackModel.getId() + ".json");
                configObjectWriter.writeValue(writeFile.toFile(), testPackModel);
            }
            String functionName = node.pipelineNode.getFunction().getName();
            String took = formatNanos(node.lastSampleFinished.get());
            int downstreamCount = pipelineTree.downstreamCount(node.pipelineNode);
            if (downstreamCount == 0) {
                LOGGER.info("Generated {} {} test packs for {}, took {}", node.sampleFutures.size(), transformType, functionName, took);
            } else {
                LOGGER.info("Generated {} {} test packs for {}, took {}; output read by {} downstream functions",
                        node.sampleFutures.size(), transformType, functionName, took, downstreamCount);
            }
        }
        LOGGER.info("Generated {} samples for {} functions at depth {}, took {}", samples, nodes.size(), depth(level.get(0)), levelStopwatch);
    }

    /**
     * Prepares one node for generation: finds its input samples, groups them by test pack after applying the tree-wide
     * filter, the node's own filter and any {@link PipelineTestPackFilter}, then builds its pipeline model, its function
     * runner and its output XSD schema once, to be shared by all its samples.
     *
     * @return the node's work, with no samples if no test pack passes the filters; or null if the node is excluded from
     * test pack generation
     */
    private NodeWork prepareNode(PipelineNode pipelineNode, PipelineTreeConfig config, Path resourcesPath, ObjectWriter jsonObjectWriter) throws IOException {
        TransformType transformType = pipelineNode.getTransformType();
        String functionName = pipelineNode.getFunction().getName();
        LOGGER.debug("Generating {} test packs for {} ", transformType, functionName);

        final PipelineTestPackFilter pipelineTestPackFilter = config.getTestPackFilter();
        if (pipelineTestPackFilter != null && pipelineTestPackFilter.getExcludedFunctionsFromTestPackGeneration().contains(pipelineNode.getFunction())) {
            LOGGER.debug("Aborting {} Test Pack Generation for {} as this has been excluded from Test Pack generation", transformType, functionName);
            return null;
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

        if (filteredTestPackToSamples.isEmpty()) {
            return new NodeWork(pipelineNode, inputPath, outputPath, null, null, filteredTestPackToSamples, config);
        }

        PipelineModel pipeline = pipelineModelBuilder.build(pipelineNode, config);
        PipelineModel.Transform transform = pipeline.getTransform();
        Class<? extends RosettaModelObject> inputType = toClass(transform.getInputType());
        Class<? extends RosettaModelObject> functionType = toClass(transform.getFunction());
        Class<? extends RosettaModelObject> outputType = toClass(transform.getOutputType());
        // XSD validation. A Schema (not a Validator) is passed through since samples are generated concurrently,
        // and javax.xml.validation.Validator is not thread-safe.
        Schema outputXsdSchema = Optional.ofNullable(config.getXmlSchemaMap())
                .map(sm -> getXsdSchema(outputType, sm))
                .orElse(null);

        PipelineFunctionRunner functionRunner =
                functionRunnerProvider.create(transform.getType(),
                        inputType,
                        functionType,
                        pipeline.getInputSerialisation(),
                        pipeline.getOutputSerialisation(),
                        defaultJsonObjectMapper,
                        jsonObjectWriter,
                        outputXsdSchema);

        return new NodeWork(pipelineNode, inputPath, outputPath, pipeline, functionRunner, filteredTestPackToSamples, config);
    }

    /**
     * Runs the node's function on one sample and writes the output under the node's output path, with the output
     * format's file extension. Called concurrently from the worker threads: it touches only this sample's files, and
     * adds the validation report to the shared summariser under a lock.
     *
     * @return the sample's entry for the test pack config
     */
    private TestPackModel.SampleModel generateSample(Path resourcesPath,
                                                      NodeWork node,
                                                      String testPackId,
                                                      Path inputSample,
                                                      ValidationSummariser validationSummariser) throws IOException {
        TransformType transformType = node.pipelineNode.getTransformType();
        LOGGER.debug("Generating {} function {} test pack {} sample {}", transformType, node.pipelineNode.getFunction().getSimpleName(), testPackId, inputSample.getFileName());

        Path relativeOutputPath = resourcesPath.relativize(node.outputPath.resolve(resourcesPath.relativize(node.inputPath).relativize(inputSample)));
        Path outputPath = relativeOutputPath.getParent().resolve(Path.of(updateFileExtensionBasedOnOutputFormat(node.pipeline, relativeOutputPath.toFile().getName())));

        PipelineFunctionResult result = node.functionRunner.run(resourcesPath.resolve(inputSample));
        TestPackModel.SampleModel.Assertions assertions = result.getAssertions();

        String baseFileName = getBaseFileName(inputSample.toUri().toURL());
        String displayName = baseFileName.replace("-", " ");

        // Sample paths are stored in the test-pack model and resolved as classpath
        // resources, so they always use "/" regardless of the platform separator
        TestPackModel.SampleModel sampleModel = new TestPackModel.SampleModel(baseFileName.toLowerCase(), displayName, toPortableString(inputSample), toPortableString(outputPath), assertions);

        Files.createDirectories(resourcesPath.resolve(outputPath).getParent());
        Files.write(resourcesPath.resolve(outputPath), result.getSerialisedOutput().getBytes());

        if (validationSummariser != null) {
            ValidationReport validationReport = result.getValidationReport();
            // addValidationReport implementations aren't guaranteed thread-safe, and samples share one summariser
            synchronized (validationSummariser) {
                validationSummariser.addValidationReport(node.pipeline, sampleModel.getName(), sampleModel, validationReport);
            }
        }
        return sampleModel;
    }

    /**
     * Waits for a sample and returns its result, rethrowing the exception it failed with: an {@link IOException},
     * {@link RuntimeException} or {@link Error} as it was, anything else wrapped in an {@link IllegalStateException}.
     */
    private static <T> T await(Future<T> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while generating test pack samples", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        }
    }

    /**
     * Formats a duration in nanoseconds as seconds with three decimals, for the per-node log line.
     */
    private static String formatNanos(long nanos) {
        return String.format(Locale.ROOT, "%.3f s", nanos / 1e9);
    }

    /**
     * One node's samples for a level: its paths, pipeline and function runner, created once and shared by its samples.
     */
    private static final class NodeWork {

        private final PipelineNode pipelineNode;
        private final Path inputPath;
        private final Path outputPath;
        private final PipelineModel pipeline;
        private final PipelineFunctionRunner functionRunner;
        private final Map<String, List<Path>> testPackToSamples;
        private final String pipelineId;
        private final String pipelineIdSuffix;
        private final Map<String, List<Future<TestPackModel.SampleModel>>> sampleFutures = new LinkedHashMap<>();
        private final AtomicLong lastSampleFinished = new AtomicLong();

        /**
         * @param pipeline         the node's pipeline model, or null when it has no samples
         * @param functionRunner   the runner shared by the node's samples, or null when it has no samples
         * @param testPackToSamples the node's input samples, grouped by test pack id
         */
        private NodeWork(PipelineNode pipelineNode, Path inputPath, Path outputPath, PipelineModel pipeline, PipelineFunctionRunner functionRunner,
                         Map<String, List<Path>> testPackToSamples, PipelineTreeConfig config) {
            this.pipelineNode = pipelineNode;
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.pipeline = pipeline;
            this.functionRunner = functionRunner;
            this.testPackToSamples = testPackToSamples;
            this.pipelineId = pipelineNode.id(config.isStrictUniqueIds());
            this.pipelineIdSuffix = pipelineNode.idSuffix(config.isStrictUniqueIds(), "-");
        }
    }

    /**
     * Names the pool's threads {@code testpack-worker-N}, so they can be told apart in logs and thread dumps, and makes
     * them daemon threads, so a pool left running can't keep the JVM alive.
     */
    private static final class TestPackWorkerThreadFactory implements ThreadFactory {

        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "testpack-worker-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    @NotNull
    private String updateFileExtensionBasedOnOutputFormat(PipelineModel pipelineModel, String fileName) {
        String outputFormat = Optional.ofNullable(pipelineModel.getOutputSerialisation())
                .map(PipelineModel.Serialisation::getFormat)
                .map(PipelineModel.Serialisation.Format::getFileExtension)
                .orElse("json");
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

    /**
     * Loads the XSD schema configured for the function's output type, or returns null if none is configured. A
     * {@link Schema} is thread-safe, so one is shared by all the node's samples; each sample creates its own
     * {@code Validator} from it.
     */
    private Schema getXsdSchema(Class<?> functionType, ImmutableMap<Class<?>, String> outputSchemaMap) {
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
            return schemaFactory.newSchema(schemaUrl);
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
