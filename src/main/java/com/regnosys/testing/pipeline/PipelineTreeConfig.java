package com.regnosys.testing.pipeline;

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
import java.util.stream.Collectors;

public class PipelineTreeConfig {

    private ImmutableMap<Class<?>, String> xmlConfigMap;
    private ImmutableMap<Class<?>, String> xmlSchemaMap;
    private final List<TransformFunction> starting = new ArrayList<>();

    private final Multimap<Class<? extends RosettaFunction>, TransformFunction> conf = ArrayListMultimap.create();
    private boolean strictUniqueIds;
    private Path writePath;

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

    public PipelineTreeConfig withXmlConfigMap(ImmutableMap<Class<?>, String> xmlConfigMap) {
        this.xmlConfigMap = xmlConfigMap;
        return this;
    }

    public PipelineTreeConfig withXmlSchemaMap(ImmutableMap<Class<?>, String> xmlSchemaMap) {
        this.xmlSchemaMap = xmlSchemaMap;
        return this;
    }

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
