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
