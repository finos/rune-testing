package com.regnosys.testing.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

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
}