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

import com.regnosys.rosetta.common.transform.FunctionNameHelper;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.rosetta.model.lib.functions.RosettaFunction;
import com.rosetta.model.lib.transform.Ingest;
import com.rosetta.model.lib.transform.Projection;
import com.rosetta.model.lib.transform.SerializationFormat;
import org.apache.commons.lang3.StringUtils;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


public class PipelineModelBuilder {

    private final FunctionNameHelper helper;

    @Inject
    public PipelineModelBuilder(FunctionNameHelper helper) {
        this.helper = helper;
    }

    public List<PipelineModel> createPipelineModels(PipelineTree pipelineTree) {
        return pipelineTree.getNodeList().stream()
                .map(modelBuilder -> build(modelBuilder, pipelineTree.getPipelineTreeConfig()))
                .collect(Collectors.toList());
    }

    protected PipelineModel build(PipelineNode modelBuilder, PipelineTreeConfig config) {
        Class<? extends RosettaFunction> function = modelBuilder.getFunction();
        String inputType = helper.getInputType(function);
        String outputType = helper.getOutputType(function);
        String name = helper.getName(function);

        // A serialisation-agnostic pipeline does not record the serialisation that the function's
        // @Ingest/@Projection annotation already expresses: @Ingest covers the input, @Projection the
        // output, and the object mapper for that direction is built from the annotation directly (as in
        // rosetta-products). So we deliberately omit that direction here. The opposite direction is not
        // expressed by the annotation but is still needed for testing (an @Ingest function's output is
        // serialised to compare against expectations; an @Projection function's input is deserialised),
        // so it keeps coming from the deprecated config maps on PipelineTreeConfig
        // (withXmlConfigMap/withInputSerialisationFormatMap/...).

        //Replace this with inputSerialisationFromAnnotation(function).isPresent() ? null : mapBasedInputSerialisation(function, config); once we have models with annotations rolled out.
        PipelineModel.Serialisation inputSerialisation = mapBasedInputSerialisation(function, config);
        //Replace this with outputSerialisationFromAnnotation(function).isPresent() ? null : mapBasedOutputSerialisation(function, config);once we have models with annotations rolled out.
        PipelineModel.Serialisation outputSerialisation = mapBasedOutputSerialisation(function, config);

        String pipelineId = modelBuilder.id(config.isStrictUniqueIds());
        String upstreamPipelineId = modelBuilder.upstreamId(config.isStrictUniqueIds());

        String modelId = config.getModelId();
        String prefixedPipelineName = StringUtils.isEmpty(modelId) ? name : String.format("%s %s", modelId, name);
        
        return new PipelineModel(pipelineId,
                prefixedPipelineName,
                new PipelineModel.Transform(modelBuilder.getTransformType(), modelBuilder.getFunction().getName(), inputType, outputType),
                upstreamPipelineId,
                inputSerialisation,
                outputSerialisation,
                modelId
        );
    }

    /**
     * Whether the function carries an {@link Ingest} or {@link Projection} transform annotation. The
     * annotation supplies the serialisation for the direction it governs (the input for {@code @Ingest},
     * the output for {@code @Projection}); the opposite direction is not expressed by the annotation and
     * still comes from the deprecated pipeline config maps.
     */
    static boolean hasTransformAnnotation(Class<? extends RosettaFunction> function) {
        return function.isAnnotationPresent(Ingest.class) || function.isAnnotationPresent(Projection.class);
    }

    /**
     * Derives the input serialisation from the function's {@link Ingest} annotation, if present.
     */
    static Optional<PipelineModel.Serialisation> inputSerialisationFromAnnotation(Class<? extends RosettaFunction> function) {
        Ingest ingest = function.getAnnotation(Ingest.class);
        return ingest == null ? Optional.empty() : Optional.of(toSerialisation(ingest.format(), ingest.configPath()));
    }

    /**
     * Derives the output serialisation from the function's {@link Projection} annotation, if present.
     */
    static Optional<PipelineModel.Serialisation> outputSerialisationFromAnnotation(Class<? extends RosettaFunction> function) {
        Projection projection = function.getAnnotation(Projection.class);
        return projection == null ? Optional.empty() : Optional.of(toSerialisation(projection.format(), projection.configPath()));
    }

    static PipelineModel.Serialisation toSerialisation(SerializationFormat format, String configPath) {
        return toSerialisation(PipelineModel.Serialisation.Format.valueOf(format.name()), configPath);
    }

    /**
     * Builds a {@link PipelineModel.Serialisation} from a format and config path, treating every format
     * uniformly: any format may carry a config path, and none is given special treatment.
     */
    static PipelineModel.Serialisation toSerialisation(PipelineModel.Serialisation.Format format, String configPath) {
        if (format == null) {
            return null;
        }
        String resolvedConfigPath = (configPath == null || configPath.isEmpty()) ? null : configPath;
        return new PipelineModel.Serialisation(format, resolvedConfigPath);
    }

    /**
     * @deprecated The serialisation format and config path are now derived from the function's
     * {@link Ingest}/{@link Projection} annotation (see {@link #inputSerialisationFromAnnotation}).
     * This map-based fallback only exists for models whose code generator does not yet emit those
     * annotations and will be removed once they all do.
     */
    @Deprecated
    private PipelineModel.Serialisation mapBasedInputSerialisation(Class<? extends RosettaFunction> function, PipelineTreeConfig config) {
        Class<?> inputClass = helper.getInputClass(function);
        String configPath = config.getXmlConfigMap().get(inputClass);
        PipelineModel.Serialisation.Format format = Optional.ofNullable(config.getInputSerialisationFormatMap())
                .map(formatMap -> formatMap.get(inputClass))
                .orElse(null);
        return toSerialisation(format, configPath);
    }

    /**
     * @deprecated The serialisation format and config path are now derived from the function's
     * {@link Ingest}/{@link Projection} annotation (see {@link #outputSerialisationFromAnnotation}).
     * This map-based fallback only exists for models whose code generator does not yet emit those
     * annotations and will be removed once they all do.
     */
    @Deprecated
    private PipelineModel.Serialisation mapBasedOutputSerialisation(Class<? extends RosettaFunction> function, PipelineTreeConfig config) {
        Class<?> returnType = helper.getFuncMethod(function).getReturnType();
        String configPath = config.getXmlConfigMap().get(returnType);
        PipelineModel.Serialisation.Format format = Optional.ofNullable(config.getOutputSerialisationFormatMap())
                .map(formatMap -> formatMap.get(returnType))
                .orElse(null);
        return toSerialisation(format, configPath);
    }
}
