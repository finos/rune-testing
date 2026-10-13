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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.regnosys.rosetta.common.transform.TransformType;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.regnosys.testing.pipeline.PipelineFilter.equalsTo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PipelineTreeBuilderTest {

    @Inject
    private PipelineTreeBuilder pipelineTreeBuilder;

    @Inject
    PipelineTestHelper helper;

    @BeforeEach
    void setUp() {
        PipelineTestHelper.setupInjector(this);
    }

    @Test
    void createPipelineTree() {
        final PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createTreeConfig());
        assertEquals( 3, pipelineTree.getNodeList().size());
    }

    @Test
    void createPipelineTreeNoStarting() {
        final PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createTreeConfigWithoutStarting());
        assertEquals( 0, pipelineTree.getNodeList().size());
    }

    @Test
    void createPipelineTreeMultipleStarting() {
        final PipelineTree pipelineTree = pipelineTreeBuilder.createPipelineTree(helper.createNestedTreeConfigMultipleStartingNodes());
        assertEquals( 6, pipelineTree.getNodeList().size());
    }

    @Test
    void createPipelineTreeNullConfig() {
        assertThrows( PipelineTreeCreationException.class, () -> pipelineTreeBuilder.createPipelineTree(null));
    }

    @Test
    void createPipelineTreeRemovesDuplicateNodes() {
        PipelineTreeConfig config = new PipelineTreeConfig("testPrefix")
                .starting(TransformType.REPORT, helper.middleAClass())
                .add(helper.middleAClass(), TransformType.PROJECTION, helper.endAClass())
                .starting(TransformType.REPORT, helper.middleAClass())
                .add(helper.middleAClass(), TransformType.PROJECTION, helper.endAClass())
                .add(helper.middleAClass(), TransformType.PROJECTION, helper.endBClass());

        List<String> ids = pipelineTreeBuilder.createPipelineTree(config).getNodeList().stream()
                .map(n -> n.id(true))
                .collect(Collectors.toList());

        assertEquals(List.of(
                "pipeline-report-testPrefix-middle-a",
                "pipeline-projection-testPrefix-middle-a-end-a",
                "pipeline-projection-testPrefix-middle-a-end-b"), ids);
    }

    @Test
    void createPipelineTreeCascadesTestPackFilters() {
        PipelineTreeConfig config = new PipelineTreeConfig("testPrefix")
                .starting(TransformType.ENRICH, helper.startClass(), equalsTo("tp-1", "tp-2", "tp-3"))
                .add(helper.startClass(), TransformType.REPORT, helper.middleAClass(), equalsTo("tp-1", "tp-2"))
                .add(helper.startClass(), TransformType.REPORT, helper.middleBClass())
                .add(helper.middleAClass(), TransformType.PROJECTION, helper.endAClass(), equalsTo("tp-2", "tp-3"));

        Map<String, PipelineNode> nodes = nodesById(config);

        assertAccepts(nodes.get("pipeline-enrich-testPrefix-start"), "tp-1", "tp-2", "tp-3");
        assertAccepts(nodes.get("pipeline-report-testPrefix-start-middle-a"), "tp-1", "tp-2");
        assertAccepts(nodes.get("pipeline-report-testPrefix-start-middle-b"), "tp-1", "tp-2", "tp-3");
        assertAccepts(nodes.get("pipeline-projection-testPrefix-start-middle-a-end-a"), "tp-2");
    }

    @Test
    void createPipelineTreeAppliesTestPackFilterPerUpstreamFunction() {
        PipelineTreeConfig config = new PipelineTreeConfig("testPrefix")
                .starting(TransformType.ENRICH, helper.middleAClass())
                .starting(TransformType.ENRICH, helper.middleBClass(), equalsTo("tp-pre"))
                .add(helper.middleAClass(), TransformType.REPORT, helper.endClass(), equalsTo("tp-1"))
                .add(helper.middleBClass(), TransformType.REPORT, helper.endClass());

        Map<String, PipelineNode> nodes = nodesById(config);

        assertAccepts(nodes.get("pipeline-report-testPrefix-middle-a-end"), "tp-1");
        assertAccepts(nodes.get("pipeline-report-testPrefix-middle-b-end"), "tp-pre");
    }

    @Test
    void createPipelineTreeUnionsFiltersOfRepeatedLink() {
        PipelineTreeConfig config = new PipelineTreeConfig("testPrefix")
                .starting(TransformType.ENRICH, helper.startClass())
                .add(helper.startClass(), TransformType.REPORT, helper.middleClass(), equalsTo("tp-1"))
                .add(helper.startClass(), TransformType.REPORT, helper.middleClass(), equalsTo("tp-2"));

        Map<String, PipelineNode> nodes = nodesById(config);

        assertEquals(2, nodes.size());
        assertAccepts(nodes.get("pipeline-report-testPrefix-start-middle"), "tp-1", "tp-2");
    }

    @Test
    void createPipelineTreeKeepsAllTestPacksWhenRepeatedLinkHasNoFilter() {
        PipelineTreeConfig filteredFirst = new PipelineTreeConfig("testPrefix")
                .starting(TransformType.ENRICH, helper.startClass())
                .add(helper.startClass(), TransformType.REPORT, helper.middleClass(), equalsTo("tp-1"))
                .add(helper.startClass(), TransformType.REPORT, helper.middleClass());
        PipelineTreeConfig unfilteredFirst = new PipelineTreeConfig("testPrefix")
                .starting(TransformType.ENRICH, helper.startClass())
                .add(helper.startClass(), TransformType.REPORT, helper.middleClass())
                .add(helper.startClass(), TransformType.REPORT, helper.middleClass(), equalsTo("tp-1"));

        assertAccepts(nodesById(filteredFirst).get("pipeline-report-testPrefix-start-middle"), "tp-1", "tp-2", "tp-3", "tp-pre");
        assertAccepts(nodesById(unfilteredFirst).get("pipeline-report-testPrefix-start-middle"), "tp-1", "tp-2", "tp-3", "tp-pre");
    }

    @Test
    void createPipelineTreeKeepsAllTestPacksWhenRepeatedStartingHasNoFilter() {
        PipelineTreeConfig config = new PipelineTreeConfig("testPrefix")
                .starting(TransformType.ENRICH, helper.startClass())
                .starting(TransformType.ENRICH, helper.startClass(), equalsTo("tp-1"));

        assertAccepts(nodesById(config).get("pipeline-enrich-testPrefix-start"), "tp-1", "tp-2", "tp-3", "tp-pre");
    }

    private Map<String, PipelineNode> nodesById(PipelineTreeConfig config) {
        return pipelineTreeBuilder.createPipelineTree(config).getNodeList().stream()
                .collect(Collectors.toMap(n -> n.id(true), Function.identity()));
    }

    private static void assertAccepts(PipelineNode node, String... expectedTestPackIds) {
        List<String> accepted = List.of("tp-1", "tp-2", "tp-3", "tp-pre").stream()
                .filter(node.getTestPackIdFilter())
                .collect(Collectors.toList());
        assertEquals(List.of(expectedTestPackIds), accepted, node.id(true));
    }
}
