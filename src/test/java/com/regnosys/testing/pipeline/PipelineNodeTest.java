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
import com.regnosys.rosetta.common.transform.TransformType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

class PipelineNodeTest {

    @Inject
    private FunctionNameHelper functionNameHelper;

    private static PipelineNode ENRICH_NODE;

    private static PipelineNode REPORT_NODE;

    private static PipelineNode PROJECTION_NODE;

    private static final boolean STRICT_IDS = true;

    private static final boolean NO_STRICT_IDs = false;

    @BeforeEach
    void setUp() {
        PipelineTestHelper.setupInjector(this);
        ENRICH_NODE = new PipelineNode(functionNameHelper, TransformType.ENRICH, PipelineTestUtils.Enrich_Type_1ToType_2.class, null);
        REPORT_NODE = new PipelineNode(functionNameHelper, TransformType.REPORT, PipelineTestUtils.Report_Type_2ToType_3.class, ENRICH_NODE);
        PROJECTION_NODE = new PipelineNode(functionNameHelper, TransformType.PROJECTION, PipelineTestUtils.Project_Type_3ToType_4.class, REPORT_NODE);
    }

    @Test
    void id() {
        assertEquals("pipeline-enrich-type1-to-type2", ENRICH_NODE.id(STRICT_IDS));
    }

    @Test
    void idWithoutStrictIds() {
        assertEquals("pipeline-enrich-type1-to-type2", ENRICH_NODE.id(NO_STRICT_IDs));
    }

    @Test
    void nullUpstream() {
        assertNull(ENRICH_NODE.upstreamId(STRICT_IDS));
    }

    @Test
    void upstreamId() {
        assertEquals("pipeline-report-type1-to-type2-type2-to-type3", PROJECTION_NODE.upstreamId(STRICT_IDS));
    }

    @Test
    void upstreamIdWithoutStrictIds() {
        assertEquals("pipeline-report-type2-to-type3", PROJECTION_NODE.upstreamId(NO_STRICT_IDs));
    }

    @Test
    void idSuffixParentNode() {
        assertEquals("type1-to-type2", ENRICH_NODE.idSuffix(STRICT_IDS, "-"));
    }

    @Test
    void idSuffixWithoutStrictIdsParentNode() {
        assertEquals("type1-to-type2", ENRICH_NODE.idSuffix(STRICT_IDS, "-"));
    }

    @Test
    void idSuffixChildNode() {
        assertEquals("type1-to-type2-type2-to-type3-type3-to-type4", PROJECTION_NODE.idSuffix(STRICT_IDS, "-"));
    }

    @Test
    void idSuffixWithoutStrictIdsChildNode() {
        assertEquals("type3-to-type4", PROJECTION_NODE.idSuffix(NO_STRICT_IDs, "-"));
    }
}
