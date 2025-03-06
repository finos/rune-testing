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
import com.google.common.collect.Multimap;
import com.regnosys.rosetta.common.transform.TransformType;
import com.rosetta.model.lib.functions.RosettaFunction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PipelineTreeConfig {

    private ImmutableMap<Class<?>, String> xmlConfigMap;
    private ImmutableMap<Class<?>, String> xmlSchemaMap;
    private final List<TransformFunction> starting = new ArrayList<>();
    private PipelineTestPackFilter pipelineTestPackFilter;

    private final Multimap<Class<? extends RosettaFunction>, TransformFunction> conf = ArrayListMultimap.create();
    private boolean strictUniqueIds;
    private Path writePath;
    private Predicate<String> testPackIdFilter = testPackId -> true;
    private String modelId;


/**
 * @deprecated This constructor is here to prevent existing model extensions from breaking. It will be removed in the future
 * as part of wider work. on the PipelineTreeConfig class.
 */
    @Deprecated
    public PipelineTreeConfig() {
        this("");
    }

    public PipelineTreeConfig(String modelId) {
        this.modelId = modelId;
    }

    public PipelineTreeConfig starting(TransformType transformType, Class<? extends RosettaFunction> function) {
        starting.add(new TransformFunction(function, transformType));
        return this;
    }

    public PipelineTreeConfig strictUniqueIds() {
        strictUniqueIds = true;
        return this;
    }

    public PipelineTreeConfig add(Class<? extends RosettaFunction> upstreamFunction, TransformType transformType, Class<? extends RosettaFunction> function) {
        TransformFunction current = new TransformFunction(function, transformType);
        conf.put(upstreamFunction, current);
        return this;
    }
    
    public PipelineTreeConfig withTestPackIdFilter(Predicate<String> testPackIdFilter) {
        this.testPackIdFilter = testPackIdFilter;
        return this;
    }
    
    public PipelineTreeConfig withXmlConfigMap(ImmutableMap<Class<?>, String> xmlConfigMap) {
        this.xmlConfigMap = xmlConfigMap;
        return this;
    }

    public PipelineTreeConfig withXmlSchemaMap(ImmutableMap<Class<?>, String> xmlSchemaMap) {
        this.xmlSchemaMap = xmlSchemaMap;
        return this;
    }

    public String getModelId() { return modelId; }

    public ImmutableMap<Class<?>, String> getXmlConfigMap() {
        return Optional.ofNullable(xmlConfigMap).orElse(ImmutableMap.of());
    }

    public ImmutableMap<Class<?>, String> getXmlSchemaMap() {
        return xmlSchemaMap;
    }

    public PipelineTreeConfig withWritePath(Path writePath) {
        this.writePath = writePath;
        return this;
    }

    public Path getWritePath() {
        return writePath;
    }

    public boolean isStrictUniqueIds() {
        return strictUniqueIds;
    }

    List<TransformFunction> getStarting() {
        return starting;
    }

    public PipelineTreeConfig withTestPackFilter(PipelineTestPackFilter pipelineTestPackFilter) {
        this.pipelineTestPackFilter = pipelineTestPackFilter;
        return this;
    }
    
    public Predicate<String> getTestPackIdFilter() {
        return testPackIdFilter;
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
