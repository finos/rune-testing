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
import org.apache.commons.lang3.StringUtils;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.regnosys.rosetta.common.transform.TestPackUtils.getSerialisation;


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
        String inputType = helper.getInputType(modelBuilder.getFunction());
        String outputType = helper.getOutputType(modelBuilder.getFunction());
        String inputSerialisationConfigPath = config.getXmlConfigMap().get(helper.getInputClass(modelBuilder.getFunction()));
        String outputSerialisationConfigPath = config.getXmlConfigMap().get(helper.getFuncMethod(modelBuilder.getFunction()).getReturnType());
        PipelineModel.Serialisation.Format inputSerialisatinoFormat = Optional.ofNullable(config.getInputSerialisationFormatMap())
                .map(formatMap -> formatMap.get(helper.getInputClass(modelBuilder.getFunction())))
                .orElse(null);
        PipelineModel.Serialisation.Format outputSerialisationFormat = Optional.ofNullable(config.getOutputSerialisationFormatMap())
                .map(formatMap ->  formatMap.get(helper.getFuncMethod(modelBuilder.getFunction()).getReturnType()))
                .orElse(null);
        String name = helper.getName(modelBuilder.getFunction());

        // assume XML when serialisation format is null and config is populated
        PipelineModel.Serialisation inputSerialisation = getSerialisation(inputSerialisatinoFormat, inputSerialisationConfigPath);
        PipelineModel.Serialisation outputSerialisation = getSerialisation(outputSerialisationFormat, outputSerialisationConfigPath);

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
}
