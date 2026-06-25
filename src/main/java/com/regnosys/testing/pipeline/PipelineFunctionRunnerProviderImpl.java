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
import com.regnosys.rosetta.common.transform.LabelProviderResolver;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackUtils;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.functions.LabelProvider;
import com.rosetta.model.lib.functions.RosettaFunction;
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

    @Override
    public PipelineFunctionRunner create(TransformType transformType,
                                         Class<? extends RosettaModelObject> inputType,
                                         Class<?> functionType,
                                         PipelineModel.Serialisation inputSerialisation,
                                         PipelineModel.Serialisation outputSerialisation,
                                         ObjectMapper defaultJsonObjectMapper,
                                         ObjectWriter defaultJsonObjectWriter,
                                         Validator outputXsdValidator) {
        // Input/output (de)serialisation is resolved by TestPackUtils: the pipeline's serialisation when
        // present, otherwise the function's @Ingest/@Projection annotation (which a serialisation-agnostic
        // pipeline omits), otherwise the default JSON mapper/writer. The CSV_LABELLED format of a pipeline
        // outputSerialisation needs a LabelProvider resolved from the function class (@RuneLabelProvider);
        // the annotation path resolves its own inside TransformObjectMapperFactory.
        ObjectMapper inputObjectMapper =
                TestPackUtils.getInputObjectMapper(inputSerialisation, functionType, defaultJsonObjectMapper);
        LabelProvider labelProvider = resolveLabelProvider(outputSerialisation, functionType);
        ObjectWriter outputObjectWriter =
                TestPackUtils.getOutputObjectWriter(outputSerialisation, functionType, labelProvider, defaultJsonObjectWriter);

        return createTestPackFunctionRunner(transformType,
                functionType,
                inputType,
                inputObjectMapper,
                outputObjectWriter,
                outputXsdValidator);
    }

    /**
     * Resolves the {@link LabelProvider} for the {@code CSV_LABELLED} output format from the
     * transform function class (which carries the generated {@code @RuneLabelProvider}). Returns
     * {@code null} for every other format, so the provider is never resolved unnecessarily.
     * When the format is {@code CSV_LABELLED} but no provider can be resolved (function class
     * missing or not a {@link RosettaFunction}), {@code null} is returned and
     * {@link TestPackUtils#getObjectWriter(PipelineModel.Serialisation, LabelProvider)} throws.
     */
    private static LabelProvider resolveLabelProvider(PipelineModel.Serialisation outputSerialisation, Class<?> functionType) {
        if (outputSerialisation == null
                || outputSerialisation.getFormat() != PipelineModel.Serialisation.Format.CSV_LABELLED
                || functionType == null
                || !RosettaFunction.class.isAssignableFrom(functionType)) {
            return null;
        }
        return LabelProviderResolver.fromTransformFunction(functionType.asSubclass(RosettaFunction.class));
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
