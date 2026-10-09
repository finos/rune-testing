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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.testing.validation.ValidationSummariser;
import com.rosetta.model.lib.functions.RosettaFunction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PipelineTreeConfig {

    // Links added without a filter store this, so a filter added for the same link elsewhere unions to all test packs
    private static final Predicate<String> ALL_TEST_PACKS = testPackId -> true;

    private final List<TransformFunction> starting = new ArrayList<>();
    private final String modelId;
    private final Multimap<Class<? extends RosettaFunction>, TransformFunction> conf = ArrayListMultimap.create();
    private final Map<Edge, Predicate<String>> edgeTestPackIdFilters = new HashMap<>();
    
    private ImmutableMap<Class<?>, String> xmlConfigMap;
    private ImmutableMap<Class<?>, String> xmlSchemaMap;
    private ImmutableMap<Class<?>, PipelineModel.Serialisation.Format> inputSerialisationFormatMap;
    private ImmutableMap<Class<?>, PipelineModel.Serialisation.Format> outputSerialisationFormatMap;
    private Boolean sortJsonPropertiesAlphabetically;
    private PipelineTestPackFilter pipelineTestPackFilter;
    private boolean strictUniqueIds;
    private Path writePath;
    private Predicate<String> testPackIdFilter = testPackId -> true;
    private ImmutableSet<Path> csvTestPackSourceFiles;
    private ValidationSummariser validationSummariser;

    /**
     * Use this constructor when the Transform functions used in the tree config are unique to a model.
     * When re-using functions that are shared between models, use the constructor with the modelId.
     */
    public PipelineTreeConfig() {
        this(null);
    }

    public PipelineTreeConfig(String modelId) {
        this.modelId = modelId;
    }

    public String getModelId() {
        return modelId;
    }

    public PipelineTreeConfig strictUniqueIds() {
        strictUniqueIds = true;
        return this;
    }

    public boolean isStrictUniqueIds() {
        return strictUniqueIds;
    }

    /**
     * Adds a starting function: a node with no upstream function, which reads its samples from its transform type's
     * input folder. It generates every test pack found there, subject only to the tree-wide filter.
     */
    public PipelineTreeConfig starting(TransformType transformType, Class<? extends RosettaFunction> function) {
        return starting(transformType, function, ALL_TEST_PACKS);
    }

    /**
     * Adds a starting function that only generates test packs whose id matches the given filter.
     * The filter cascades: every function downstream of this one is also restricted to these test packs.
     */
    public PipelineTreeConfig starting(TransformType transformType, Class<? extends RosettaFunction> function, Predicate<String> testPackIdFilter) {
        starting.add(new TransformFunction(function, transformType));
        addEdgeTestPackIdFilter(new Edge(null, function), testPackIdFilter);
        return this;
    }

    List<TransformFunction> getStarting() {
        return starting;
    }

    /**
     * Adds a function that reads the output of the upstream function, with no filter on that link: it generates every
     * test pack its upstream function generated.
     */
    public PipelineTreeConfig add(Class<? extends RosettaFunction> upstreamFunction, TransformType transformType, Class<? extends RosettaFunction> function) {
        return add(upstreamFunction, transformType, function, ALL_TEST_PACKS);
    }

    /**
     * Adds a downstream function that, when fed by the given upstream function, only generates test packs whose id matches
     * the given filter. The filter cascades: a node's effective filter is the intersection of the filters on the path from
     * its starting function, so functions further downstream are also restricted to these test packs.
     * <p>
     * The filter belongs to the upstream-to-function link rather than the function, so the same function can be given a
     * different filter under a different upstream function. Adding the same link more than once unions the filters, and
     * adding it without a filter accepts all test packs, so merging trees never narrows a link that was unfiltered.
     */
    public PipelineTreeConfig add(Class<? extends RosettaFunction> upstreamFunction, TransformType transformType, Class<? extends RosettaFunction> function, Predicate<String> testPackIdFilter) {
        if (conf.get(upstreamFunction).stream().noneMatch(t -> t.getFunction().equals(function))) {
            conf.put(upstreamFunction, new TransformFunction(function, transformType));
        }
        addEdgeTestPackIdFilter(new Edge(upstreamFunction, function), testPackIdFilter);
        return this;
    }

    /**
     * Records the test pack filter for a link. A link added more than once keeps the union (OR) of its filters, so
     * adding it again with a different filter widens it, and adding it with {@code ALL_TEST_PACKS} leaves it unfiltered.
     */
    private void addEdgeTestPackIdFilter(Edge edge, Predicate<String> testPackIdFilter) {
        edgeTestPackIdFilters.merge(edge, testPackIdFilter, Predicate::or);
    }

    /**
     * Returns the test pack filter for the link from the upstream function (null for a starting function) to the function.
     * Links without a filter accept all test packs.
     */
    Predicate<String> getEdgeTestPackIdFilter(Class<? extends RosettaFunction> upstreamFunction, Class<? extends RosettaFunction> function) {
        return edgeTestPackIdFilters.getOrDefault(new Edge(upstreamFunction, function), ALL_TEST_PACKS);
    }

    public PipelineTreeConfig withWritePath(Path writePath) {
        this.writePath = writePath;
        return this;
    }

    public Path getWritePath() {
        return writePath;
    }

    public PipelineTreeConfig withXmlConfigMap(ImmutableMap<Class<?>, String> xmlConfigMap) {
        this.xmlConfigMap = xmlConfigMap;
        return this;
    }

    public ImmutableMap<Class<?>, String> getXmlConfigMap() {
        return Optional.ofNullable(xmlConfigMap).orElse(ImmutableMap.of());
    }

    public PipelineTreeConfig withXmlSchemaMap(ImmutableMap<Class<?>, String> xmlSchemaMap) {
        this.xmlSchemaMap = xmlSchemaMap;
        return this;
    }

    public ImmutableMap<Class<?>, String> getXmlSchemaMap() {
        return xmlSchemaMap;
    }

    public PipelineTreeConfig withInputSerialisationFormatMap(ImmutableMap<Class<?>, PipelineModel.Serialisation.Format> inputSerialisationFormatMap) {
        this.inputSerialisationFormatMap = inputSerialisationFormatMap;
        return this;
    }

    public ImmutableMap<Class<?>, PipelineModel.Serialisation.Format> getInputSerialisationFormatMap() {
        return inputSerialisationFormatMap;
    }

    public PipelineTreeConfig withOutputSerialisationFormatMap(ImmutableMap<Class<?>, PipelineModel.Serialisation.Format> outputSerialisationFormatMap) {
        this.outputSerialisationFormatMap = outputSerialisationFormatMap;
        return this;
    }

    public ImmutableMap<Class<?>, PipelineModel.Serialisation.Format> getOutputSerialisationFormatMap() {
        return outputSerialisationFormatMap;
    }

    public PipelineTreeConfig withValidationSummariser(ValidationSummariser validationSummariser) {
        this.validationSummariser = validationSummariser;
        return this;
    }

    public ValidationSummariser getValidationSummariser() {
        return validationSummariser;
    }

    public PipelineTreeConfig withTestPackIdFilter(Predicate<String> testPackIdFilter) {
        this.testPackIdFilter = testPackIdFilter;
        return this;
    }

    public Predicate<String> getTestPackIdFilter() {
        return testPackIdFilter;
    }

    public PipelineTreeConfig withCsvTestPackSourceFiles(Collection<Path> csvTestPackSourceFiles) {
        this.csvTestPackSourceFiles = ImmutableSet.copyOf(csvTestPackSourceFiles);
        return this;
    }

    public ImmutableSet<Path> getCsvTestPackSourceFiles() {
        return Optional.ofNullable(csvTestPackSourceFiles).orElse(ImmutableSet.of());
    }

    public PipelineTreeConfig withTestPackFilter(PipelineTestPackFilter pipelineTestPackFilter) {
        this.pipelineTestPackFilter = pipelineTestPackFilter;
        return this;
    }

    PipelineTestPackFilter getTestPackFilter() {
        return pipelineTestPackFilter;
    }

    public List<Class<? extends RosettaFunction>> getDownstreamFunctions(Class<? extends RosettaFunction> function) {
        Collection<TransformFunction> transformFunctions = conf.get(function);
        return transformFunctions.stream().map(TransformFunction::getFunction).collect(Collectors.toList());
    }

    public TransformType getDownstreamTransformType(Class<? extends RosettaFunction> function) {
        Collection<TransformFunction> transformFunctions = conf.get(function);
        return transformFunctions.stream().map(TransformFunction::getTransformType).findFirst().orElse(null);
    }

    public PipelineTreeConfig withSortJsonPropertiesAlphabetically(boolean sortJsonPropertiesAlphabetically) {
        this.sortJsonPropertiesAlphabetically = sortJsonPropertiesAlphabetically;
        return this;
    }

    public boolean isSortJsonPropertiesAlphabetically() {
        return Optional.ofNullable(sortJsonPropertiesAlphabetically).orElse(true);
    }

    /**
     * A link in the tree: the upstream function (null for a starting function) and the function that reads its output.
     * Test pack filters are keyed by link, so two links are equal when both functions are.
     */
    private static final class Edge {

        private final Class<? extends RosettaFunction> upstreamFunction;
        private final Class<? extends RosettaFunction> function;

        private Edge(Class<? extends RosettaFunction> upstreamFunction, Class<? extends RosettaFunction> function) {
            this.upstreamFunction = upstreamFunction;
            this.function = function;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Edge)) return false;
            Edge edge = (Edge) o;
            return Objects.equals(upstreamFunction, edge.upstreamFunction) && Objects.equals(function, edge.function);
        }

        @Override
        public int hashCode() {
            return Objects.hash(upstreamFunction, function);
        }
    }

    static class TransformFunction {

        private final Class<? extends RosettaFunction> function;
        private final TransformType transformType;

        private TransformFunction(Class<? extends RosettaFunction> function, TransformType transformType) {
            this.function = function;
            this.transformType = transformType;
        }

        public Class<? extends RosettaFunction> getFunction() {
            return function;
        }

        public TransformType getTransformType() {
            return transformType;
        }
    }
}
