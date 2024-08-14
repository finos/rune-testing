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

import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.rosetta.model.lib.functions.RosettaFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterables.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.runners.model.MultipleFailureException.assertEmpty;

class PipelineModelBuilderTest {

    @Inject
    private PipelineTreeBuilder pipelineTreeBuilder;

    @Inject
    private PipelineModelBuilder pipelineModelBuilder;

    @Inject
    PipelineTestHelper helper;

    @BeforeEach
    void setUp() {
        PipelineTestHelper.setupInjector(this);
    }

    @Test
    void buildChain() {
        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createTreeConfig());
        List<PipelineModel> pipelineModels = pipelineModelBuilder.createPipelineModels(pipelineTree);

        Map<TransformType, List<PipelineModel>> byTransformType = pipelineModels.stream().collect(Collectors.groupingBy(x -> x.getTransform().getType()));

        PipelineModel startPipeline = getOnlyElement(byTransformType.get(TransformType.ENRICH));
        PipelineModel middlePipeline = getOnlyElement(byTransformType.get(TransformType.REPORT));
        PipelineModel endPipeline = getOnlyElement(byTransformType.get(TransformType.PROJECTION));

        assertPipelineModel(startPipeline, TransformType.ENRICH, helper.startClass(), "pipeline-enrich-start", null);
        assertPipelineModel(middlePipeline, TransformType.REPORT, helper.middleClass(), "pipeline-report-middle", "pipeline-enrich-start");
        assertPipelineModel(endPipeline, TransformType.PROJECTION, helper.endClass(), "pipeline-projection-end", "pipeline-report-middle");
    }


    @Test
    void buildChainWithUniqueIds() {
        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createTreeConfig().strictUniqueIds());
        List<PipelineModel> pipelineModels = pipelineModelBuilder.createPipelineModels(pipelineTree);

        Map<TransformType, List<PipelineModel>> byTransformType = pipelineModels.stream().collect(Collectors.groupingBy(x -> x.getTransform().getType()));

        PipelineModel startPipeline = getOnlyElement(byTransformType.get(TransformType.ENRICH));
        PipelineModel middlePipeline = getOnlyElement(byTransformType.get(TransformType.REPORT));
        PipelineModel endPipeline = getOnlyElement(byTransformType.get(TransformType.PROJECTION));

        assertPipelineModel(startPipeline, TransformType.ENRICH, helper.startClass(), "pipeline-enrich-start", null);
        assertPipelineModel(middlePipeline, TransformType.REPORT, helper.middleClass(), "pipeline-report-start-middle", "pipeline-enrich-start");
        assertPipelineModel(endPipeline, TransformType.PROJECTION, helper.endClass(), "pipeline-projection-start-middle-end", "pipeline-report-start-middle");
    }

    @Test
    void buildChainWithoutUniqueIds() {
        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createTreeConfig());
        List<PipelineModel> pipelineModels = pipelineModelBuilder.createPipelineModels(pipelineTree);

        Map<TransformType, List<PipelineModel>> byTransformType = pipelineModels.stream().collect(Collectors.groupingBy(x -> x.getTransform().getType()));

        PipelineModel startPipeline = getOnlyElement(byTransformType.get(TransformType.ENRICH));
        PipelineModel middlePipeline = getOnlyElement(byTransformType.get(TransformType.REPORT));
        PipelineModel endPipeline = getOnlyElement(byTransformType.get(TransformType.PROJECTION));

        assertPipelineModel(startPipeline, TransformType.ENRICH, helper.startClass(), "pipeline-enrich-start", null);
        assertPipelineModel(middlePipeline, TransformType.REPORT, helper.middleClass(), "pipeline-report-middle", "pipeline-enrich-start");
        assertPipelineModel(endPipeline, TransformType.PROJECTION, helper.endClass(), "pipeline-projection-end", "pipeline-report-middle");
    }


    @Test
    void buildNestedChainWithUniqueIds() {
        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createNestedTreeConfig().strictUniqueIds());
        List<PipelineModel> pipelineModels = pipelineModelBuilder.createPipelineModels(pipelineTree);

        Map<TransformType, List<PipelineModel>> byTransformType = pipelineModels.stream().collect(Collectors.groupingBy(x -> x.getTransform().getType()));

        PipelineModel startPipeline = getOnlyElement(byTransformType.get(TransformType.ENRICH));
        List<PipelineModel> middlePipelines = byTransformType.get(TransformType.REPORT);
        assertEquals(2, middlePipelines.size());

        List<PipelineModel> endPipelines = byTransformType.get(TransformType.PROJECTION);
        assertEquals(4, endPipelines.size());

        assertPipelineModel(startPipeline, TransformType.ENRICH, helper.startClass(), "pipeline-enrich-start", null);

        assertPipelineModel(middlePipelines.get(0), TransformType.REPORT, helper.middleAClass(), "pipeline-report-start-middle-a", "pipeline-enrich-start");
        assertPipelineModel(middlePipelines.get(1), TransformType.REPORT, helper.middleBClass(), "pipeline-report-start-middle-b", "pipeline-enrich-start");

        assertPipelineModel(endPipelines.get(0), TransformType.PROJECTION, helper.endAClass(), "pipeline-projection-start-middle-a-end-a", "pipeline-report-start-middle-a");
        assertPipelineModel(endPipelines.get(1), TransformType.PROJECTION, helper.endBClass(), "pipeline-projection-start-middle-a-end-b", "pipeline-report-start-middle-a");
        assertPipelineModel(endPipelines.get(2), TransformType.PROJECTION, helper.endAClass(), "pipeline-projection-start-middle-b-end-a", "pipeline-report-start-middle-b");
        assertPipelineModel(endPipelines.get(3), TransformType.PROJECTION, helper.endBClass(), "pipeline-projection-start-middle-b-end-b", "pipeline-report-start-middle-b");
    }

    @Test
    void buildNestedChainWithoutUniqueIds() {
        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createNestedTreeConfig());
        List<PipelineModel> pipelineModels = pipelineModelBuilder.createPipelineModels(pipelineTree);

        Map<TransformType, List<PipelineModel>> byTransformType = pipelineModels.stream().collect(Collectors.groupingBy(x -> x.getTransform().getType()));

        PipelineModel startPipeline = getOnlyElement(byTransformType.get(TransformType.ENRICH));
        List<PipelineModel> middlePipelines = byTransformType.get(TransformType.REPORT);
        assertEquals(2, middlePipelines.size());

        List<PipelineModel> endPipelines = byTransformType.get(TransformType.PROJECTION);
        assertEquals(4, endPipelines.size());

        assertPipelineModel(startPipeline, TransformType.ENRICH, helper.startClass(), "pipeline-enrich-start", null);

        assertPipelineModel(middlePipelines.get(0), TransformType.REPORT, helper.middleAClass(), "pipeline-report-middle-a", "pipeline-enrich-start");
        assertPipelineModel(middlePipelines.get(1), TransformType.REPORT, helper.middleBClass(), "pipeline-report-middle-b", "pipeline-enrich-start");

        assertPipelineModel(endPipelines.get(0), TransformType.PROJECTION, helper.endAClass(), "pipeline-projection-end-a", "pipeline-report-middle-a");
        assertPipelineModel(endPipelines.get(1), TransformType.PROJECTION, helper.endBClass(), "pipeline-projection-end-b", "pipeline-report-middle-a");
        assertPipelineModel(endPipelines.get(2), TransformType.PROJECTION, helper.endAClass(), "pipeline-projection-end-a", "pipeline-report-middle-b");
        assertPipelineModel(endPipelines.get(3), TransformType.PROJECTION, helper.endBClass(), "pipeline-projection-end-b", "pipeline-report-middle-b");
    }

    @Test
    void buildNestedChainWithMultipleStartNodesWithUniqueIds() {
        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createNestedTreeConfigMultipleStartingNodes().strictUniqueIds());
        List<PipelineModel> pipelineModels = pipelineModelBuilder.createPipelineModels(pipelineTree);

        Map<TransformType, List<PipelineModel>> byTransformType = pipelineModels.stream().collect(Collectors.groupingBy(x -> x.getTransform().getType()));

        List<PipelineModel> middlePipelines = byTransformType.get(TransformType.REPORT);
        assertEquals(2, middlePipelines.size());

        List<PipelineModel> endPipelines = byTransformType.get(TransformType.PROJECTION);
        assertEquals(4, endPipelines.size());

        assertPipelineModel(middlePipelines.get(0), TransformType.REPORT, helper.middleAClass(), "pipeline-report-middle-a", null);
        assertPipelineModel(middlePipelines.get(1), TransformType.REPORT, helper.middleBClass(), "pipeline-report-middle-b", null);

        assertPipelineModel(endPipelines.get(0), TransformType.PROJECTION, helper.endAClass(), "pipeline-projection-middle-a-end-a", "pipeline-report-middle-a");
        assertPipelineModel(endPipelines.get(1), TransformType.PROJECTION, helper.endBClass(), "pipeline-projection-middle-a-end-b", "pipeline-report-middle-a");
        assertPipelineModel(endPipelines.get(2), TransformType.PROJECTION, helper.endAClass(), "pipeline-projection-middle-b-end-a", "pipeline-report-middle-b");
        assertPipelineModel(endPipelines.get(3), TransformType.PROJECTION, helper.endBClass(), "pipeline-projection-middle-b-end-b", "pipeline-report-middle-b");
    }

    @Test
    void buildNestedChainWithMultipleStartNodesWithoutUniqueIds() {
        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createNestedTreeConfigMultipleStartingNodes());
        List<PipelineModel> pipelineModels = pipelineModelBuilder.createPipelineModels(pipelineTree);

        Map<TransformType, List<PipelineModel>> byTransformType = pipelineModels.stream().collect(Collectors.groupingBy(x -> x.getTransform().getType()));

        List<PipelineModel> middlePipelines = byTransformType.get(TransformType.REPORT);
        assertEquals(2, middlePipelines.size());

        List<PipelineModel> endPipelines = byTransformType.get(TransformType.PROJECTION);
        assertEquals(4, endPipelines.size());

        assertPipelineModel(middlePipelines.get(0), TransformType.REPORT, helper.middleAClass(), "pipeline-report-middle-a", null);
        assertPipelineModel(middlePipelines.get(1), TransformType.REPORT, helper.middleBClass(), "pipeline-report-middle-b", null);

        assertPipelineModel(endPipelines.get(0), TransformType.PROJECTION, helper.endAClass(), "pipeline-projection-end-a", "pipeline-report-middle-a");
        assertPipelineModel(endPipelines.get(1), TransformType.PROJECTION, helper.endBClass(), "pipeline-projection-end-b", "pipeline-report-middle-a");
        assertPipelineModel(endPipelines.get(2), TransformType.PROJECTION, helper.endAClass(), "pipeline-projection-end-a", "pipeline-report-middle-b");
        assertPipelineModel(endPipelines.get(3), TransformType.PROJECTION, helper.endBClass(), "pipeline-projection-end-b", "pipeline-report-middle-b");
    }

    @Test
    void buildNestedChainWithoutStarting() {
        PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createTreeConfigWithoutStarting().strictUniqueIds());
        List<PipelineModel> pipelineModels = pipelineModelBuilder.createPipelineModels(pipelineTree);
        assertTrue(isEmpty(pipelineTree.getNodeList()));
        assertTrue(isEmpty(pipelineModels));
    }

    private static void assertPipelineModel(PipelineModel pipelineModel, TransformType transformType, Class<? extends RosettaFunction> function, String pipelineId, String upstreamPipelineId) {
        assertEquals(transformType, pipelineModel.getTransform().getType(), "Wrong transform type");
        assertEquals(function.getName(), pipelineModel.getTransform().getFunction(), "Wrong function name");
        assertEquals(pipelineId, pipelineModel.getId(), "Wrong pipeline id");
        assertEquals(upstreamPipelineId, pipelineModel.getUpstreamPipelineId(), "Wrong upstream pipeline id");
    }
}
