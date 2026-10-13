package com.regnosys.testing.pipeline;

/*-
 * ===============
 * Rosetta Testing
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
import com.google.inject.Injector;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.postprocess.WorkflowPostProcessor;
import com.regnosys.rosetta.common.serialisation.ClasspathTransformMapperFactory;
import com.regnosys.rosetta.common.serialisation.TransformMapperFactory;
import com.regnosys.rosetta.common.serialisation.TransformSerializationResolver;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.rosetta.model.lib.RosettaModelObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import javax.xml.validation.Validator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

public class PipelineFunctionRunnerProviderImpl implements PipelineFunctionRunnerProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineFunctionRunnerProviderImpl.class);

    @Inject
    RosettaTypeValidator typeValidator;
    @Inject
    ReferenceConfig referenceConfig;
    @Inject
    Injector injector;
    @Inject
    WorkflowPostProcessor postProcessor;

    // Models on the test-pack runner live on the application classpath, so the classpath factory is the
    // right construction seam here (a runtime with isolated model classloaders implements its own).
    private final TransformMapperFactory mapperFactory = new ClasspathTransformMapperFactory();

    @Override
    @SuppressWarnings("deprecation") // the pipeline serialisation is a deprecated fallback for pre-annotation models
    public PipelineFunctionRunner create(TransformType transformType,
                                         Class<? extends RosettaModelObject> inputType,
                                         Class<?> functionType,
                                         PipelineModel.Serialisation inputSerialisation,
                                         PipelineModel.Serialisation outputSerialisation,
                                         ObjectMapper defaultJsonObjectMapper,
                                         ObjectWriter defaultJsonObjectWriter,
                                         Validator outputXsdValidator) {
        // Each side resolves from the function's @Ingest/@Projection annotation (the model's source of
        // truth), falling back to the deprecated pipeline serialisation for models generated before
        // transform annotations existed, and finally to the default JSON mapper/writer. Construction —
        // including the CSV_LABELLED @RuneLabelProvider resolution — happens in the mapper factory.
        ObjectMapper inputObjectMapper = TransformSerializationResolver.input(functionType, inputSerialisation)
                .map(serialization -> mapperFactory.create(serialization, functionType))
                .orElse(defaultJsonObjectMapper);
        ObjectWriter outputObjectWriter = TransformSerializationResolver.output(functionType, outputSerialisation)
                .map(serialization -> mapperFactory.createWriter(serialization, functionType))
                .orElse(defaultJsonObjectWriter);

        return createTestPackFunctionRunner(transformType,
                functionType,
                inputType,
                inputObjectMapper,
                outputObjectWriter,
                outputXsdValidator);
    }

    private <IN extends RosettaModelObject> PipelineFunctionRunner createTestPackFunctionRunner(TransformType transformType,
                                                                                                Class<?> functionType,
                                                                                                Class<IN> inputType,
                                                                                                ObjectMapper inputObjectMapper,
                                                                                                ObjectWriter outputObjectWriter,
                                                                                                Validator xsdValidator) {
        Function<IN, RosettaModelObject> transformFunction = getTransformFunction(functionType, inputType);
        return new PipelineFunctionRunnerImpl<>(transformType,
                transformFunction,
                inputType,
                typeValidator,
                referenceConfig,
                inputObjectMapper,
                outputObjectWriter,
                postProcessor,
                xsdValidator);
    }

    private <IN extends RosettaModelObject> Function<IN, RosettaModelObject> getTransformFunction(Class<?> functionType, Class<IN> inputType) {
        Object functionInstance = injector.getInstance(functionType);
        Method evaluateMethod;
        try {
            evaluateMethod = functionInstance.getClass().getMethod("evaluate", inputType);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(String.format("Function %s evaluate method with input type %s not found", functionType.getName(), inputType.getName()), e);
        }
        return (resolvedInput) -> {
            try {
                if (resolvedInput == null) {
                    LOGGER.info("Not invoking function {} as input is null", functionType.getName());
                    return null;
                }
                return (RosettaModelObject) evaluateMethod.invoke(functionInstance, resolvedInput);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(String.format("Failed to invoke function %s evaluate method", functionType.getName()), e.getTargetException());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(String.format("Failed to invoke function %s evaluate method", functionType.getName()), e);
            }
        };
    }
}
