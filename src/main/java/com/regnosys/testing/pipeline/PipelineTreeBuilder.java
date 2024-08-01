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

import com.regnosys.rosetta.common.transform.TransformType;
import com.rosetta.model.lib.functions.RosettaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PipelineTreeBuilder {
    private final Logger LOGGER = LoggerFactory.getLogger(PipelineTreeBuilder.class);

    private final FunctionNameHelper helper;

    @Inject
    public PipelineTreeBuilder(FunctionNameHelper helper) {
        this.helper = helper;
    }

    public PipelineTree createPipelineTree(PipelineTreeConfig pipelineTreeConfig) {
        List<PipelineTreeConfig.TransformFunction> starting = pipelineTreeConfig.getStarting();
        List<PipelineNode> nodeList = starting.stream()
                .map(t -> downstreamPipelines(pipelineTreeConfig, new PipelineNode(helper, t.getTransformType()).withFunction(t.getFunction())))
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(PipelineNode::getTransformType))
                .collect(Collectors.toList());
        return new PipelineTree(nodeList, pipelineTreeConfig);
    }


    private List<PipelineNode> downstreamPipelines(PipelineTreeConfig pipelineChainFunction, PipelineNode currentPipeline) {
        List<PipelineNode> pipelineNodes = new ArrayList<>();
        pipelineNodes.add(currentPipeline);

        TransformType downstreamTransformType = pipelineChainFunction.getDownstreamTransformType(currentPipeline.getFunction());
        if (downstreamTransformType == null) {
            return pipelineNodes;
        }
        List<PipelineNode> pipelines = createPipelineAndLinkUpstream(pipelineChainFunction, currentPipeline, downstreamTransformType);
        List<PipelineNode> downstreamPipelines = pipelines.stream()
                .map(dp -> downstreamPipelines(pipelineChainFunction, dp))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        pipelineNodes.addAll(downstreamPipelines);
        return pipelineNodes;
    }

    private List<PipelineNode> createPipelineAndLinkUpstream(PipelineTreeConfig pipelineChainFunction, PipelineNode currentPipeline, TransformType transformType) {
        List<Class<? extends RosettaFunction>> downstreamFunctions = pipelineChainFunction.getDownstreamFunctions(currentPipeline.getFunction());
        return new PipelineNode(helper, transformType)
                .linkWithUpstream(currentPipeline)
                .withFunctions(downstreamFunctions);
    }
}
