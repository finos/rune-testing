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
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PipelineTreeConfig {

    private final List<TransformFunction> starting = new ArrayList<>();
    private final String modelId;
    private final Multimap<Class<? extends RosettaFunction>, TransformFunction> conf = ArrayListMultimap.create();
    
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

    public PipelineTreeConfig starting(TransformType transformType, Class<? extends RosettaFunction> function) {
        starting.add(new TransformFunction(function, transformType));
        return this;
    }

    List<TransformFunction> getStarting() {
        return starting;
    }

    public PipelineTreeConfig add(Class<? extends RosettaFunction> upstreamFunction, TransformType transformType, Class<? extends RosettaFunction> function) {
        TransformFunction current = new TransformFunction(function, transformType);
        conf.put(upstreamFunction, current);
        return this;
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
