package com.regnosys.testing.pipeline;

import com.regnosys.rosetta.common.transform.TransformType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

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
        assertEquals("type1-to-type2-type2-to-type3-projection-type3-to-type4", PROJECTION_NODE.idSuffix(STRICT_IDS, "-"));
    }

    @Test
    void idSuffixWithoutStrictIdsChildNode() {
        assertEquals("projection-type3-to-type4", PROJECTION_NODE.idSuffix(NO_STRICT_IDs, "-"));
    }
}