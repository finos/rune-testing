package com.regnosys.testing.pipeline;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.regnosys.rosetta.common.transform.TransformType;
import com.rosetta.model.lib.functions.RosettaFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PipelineFunctionChain {

    private ImmutableMap<Class<?>, String> xmlConfigMap;
    private final List<TransformFunction> starting = new ArrayList<>();

    private final Multimap<Class<? extends RosettaFunction>, TransformFunction> conf = ArrayListMultimap.create();
    private boolean strictUniqueIds;

    public PipelineFunctionChain starting(TransformType transformType, Class<? extends RosettaFunction> function) {
        starting.add(new TransformFunction(function, transformType));
        return this;
    }

    public PipelineFunctionChain strictUniqueIds() {
        strictUniqueIds = true;
        return this;
    }

    public PipelineFunctionChain add(Class<? extends RosettaFunction> upstreamFunction, TransformType transformType, Class<? extends RosettaFunction> function) {
        TransformFunction current = new TransformFunction(function, transformType);
        conf.put(upstreamFunction, current);
        return this;
    }

    public PipelineFunctionChain withXmlConfigMap(ImmutableMap<Class<?>, String> xmlConfigMap) {
        this.xmlConfigMap = xmlConfigMap;
        return this;
    }

    public ImmutableMap<Class<?>, String> getXmlConfigMap() {
        return Optional.ofNullable(xmlConfigMap).orElse(ImmutableMap.of());
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
