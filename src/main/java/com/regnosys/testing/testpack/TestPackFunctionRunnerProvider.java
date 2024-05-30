package com.regnosys.testing.testpack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.rosetta.model.lib.RosettaModelObject;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

import static com.regnosys.testing.testpack.TestPackConfigCreatorImpl.JSON_OBJECT_MAPPER;

class TestPackFunctionRunnerProvider {
    @Inject
    private RosettaTypeValidator typeValidator;
    @Inject
    private ReferenceConfig referenceConfig;

    TestPackFunctionRunner create(PipelineModel.Transform transform, Injector injector) {
        Class<? extends RosettaModelObject> inputType = toClass(transform.getInputType());
        Class<?> functionType = toClass(transform.getFunction());
        return createTestPackFunctionRunner(functionType, inputType, injector, JSON_OBJECT_MAPPER);
    }

    TestPackFunctionRunner create(PipelineModel.Transform transform, PipelineModel.Serialisation outputSerialisation, Injector injector) {
        Class<? extends RosettaModelObject> inputType = toClass(transform.getInputType());
        Class<?> functionType = toClass(transform.getFunction());
        // TODO output serialisation
        ObjectMapper objectMapper = JSON_OBJECT_MAPPER;
        return createTestPackFunctionRunner(functionType, inputType, injector, objectMapper);
    }

    private <IN extends RosettaModelObject> TestPackFunctionRunner createTestPackFunctionRunner(Class<?> functionType, Class<IN> inputType, Injector injector, ObjectMapper objectMapper) {
        Function<IN, RosettaModelObject> transformFunction = getTransformFunction(functionType, inputType, injector);
        return new TestPackFunctionRunnerImpl<>(transformFunction, inputType, typeValidator, referenceConfig, objectMapper);
    }

    private Class<? extends RosettaModelObject> toClass(String name) {
        try {
            return (Class<? extends RosettaModelObject>) Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private <IN extends RosettaModelObject> Function<IN, RosettaModelObject> getTransformFunction(Class<?> functionType, Class<IN> inputType, Injector injector) {
        Object functionInstance = injector.getInstance(functionType);
        Method evaluateMethod;
        try {
            evaluateMethod = functionInstance.getClass().getMethod("evaluate", inputType);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(String.format("Evaluate method with input type %s not found", inputType.getName()), e);
        }
        return (resolvedInput) -> {
            try {
                return (RosettaModelObject) evaluateMethod.invoke(functionInstance, resolvedInput);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to invoke evaluate method", e);
            }
        };
    }
}
