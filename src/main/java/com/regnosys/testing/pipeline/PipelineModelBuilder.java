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

        // The @Ingest/@Projection annotation the code generator places on the function class is now the
        // single source of truth for serialisation. When the function carries such an annotation, the
        // generated pipeline deliberately omits the inputSerialisation/outputSerialisation block: the
        // annotation on the function class already describes it, so we phase the redundant block out.
        //
        // The deprecated, hand-maintained config maps on PipelineTreeConfig
        // (withXmlConfigMap/withInputSerialisationFormatMap/...) are only consulted for legacy functions
        // that do NOT carry the annotation yet. Old pipeline JSONs that still contain the serialisation
        // block continue to be read by consumers for backwards compatibility; this only affects what we
        // write.
        boolean annotationDriven = hasTransformAnnotation(function);
        PipelineModel.Serialisation inputSerialisation =
                annotationDriven ? null : mapBasedInputSerialisation(function, config);
        PipelineModel.Serialisation outputSerialisation =
                annotationDriven ? null : mapBasedOutputSerialisation(function, config);

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
     * Whether the function carries an {@link Ingest} or {@link Projection} transform annotation. When it
     * does, the annotation is the single source of truth for serialisation and the generated pipeline
     * omits the serialisation block entirely (it is no longer emitted for these functions).
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
