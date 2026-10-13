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

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.regnosys.rosetta.common.transform.FunctionNameHelper;


public class PipelineTreeBuilder {
    private final Logger LOGGER = LoggerFactory.getLogger(PipelineTreeBuilder.class);

    private final FunctionNameHelper helper;

    @Inject
    public PipelineTreeBuilder(FunctionNameHelper helper) {
        this.helper = helper;
    }

    /**
     * Builds the tree of pipeline nodes described by the config: one node per path from a starting function, each
     * carrying its effective test pack filter (see {@link PipelineNode#getTestPackIdFilter()}).
     * <p>
     * A path configured more than once, for example a repeated starting function, becomes one node, so each function
     * runs once per path. The nodes are returned sorted by transform type.
     *
     * @throws PipelineTreeCreationException if the tree can't be built
     */
    public PipelineTree createPipelineTree(PipelineTreeConfig pipelineTreeConfig) {
        try {
            List<PipelineTreeConfig.TransformFunction> starting = pipelineTreeConfig.getStarting();
            Map<String, PipelineNode> uniqueNodes = starting.stream()
                    .map(t -> new PipelineNode(pipelineTreeConfig.getModelId(), helper, t.getTransformType())
                            .withFunction(t.getFunction())
                            .withTestPackIdFilter(pipelineTreeConfig.getEdgeTestPackIdFilter(null, t.getFunction())))
                    .map(n -> downstreamPipelines(pipelineTreeConfig, n))
                    .flatMap(Collection::stream)
                    // The same function path can be configured more than once (e.g. a repeated starting function);
                    // keep one node per path so each is only executed once.
                    .collect(Collectors.toMap(n -> n.id(true), n -> n, (first, duplicate) -> first, LinkedHashMap::new));
            List<PipelineNode> nodeList = uniqueNodes.values().stream()
                    .sorted(Comparator.comparing(PipelineNode::getTransformType))
                    .collect(Collectors.toList());
            return new PipelineTree(nodeList, pipelineTreeConfig);
        }
        catch (Exception ex){
            throw new PipelineTreeCreationException("could not create pipeline tree", ex);
        }


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

    /**
     * Creates the nodes for the functions that read the given node's output, linked to it as their upstream node. Each
     * new node's test pack filter is the given node's filter AND the filter on the link between the two functions.
     */
    private List<PipelineNode> createPipelineAndLinkUpstream(PipelineTreeConfig pipelineChainFunction, PipelineNode currentPipeline, TransformType transformType) {
        List<Class<? extends RosettaFunction>> downstreamFunctions = pipelineChainFunction.getDownstreamFunctions(currentPipeline.getFunction());
        return new PipelineNode(pipelineChainFunction.getModelId(), helper, transformType)
                .linkWithUpstream(currentPipeline)
                .withFunctions(downstreamFunctions)
                .stream()
                .map(n -> n.withTestPackIdFilter(currentPipeline.getTestPackIdFilter()
                        .and(pipelineChainFunction.getEdgeTestPackIdFilter(currentPipeline.getFunction(), n.getFunction()))))
                .collect(Collectors.toList());
    }
}
