package com.regnosys.testing.pipeline;

import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.functions.RosettaFunction;

public class PipelineTestUtils {
    static class Enrich_Type_1ToType_2 implements RosettaFunction {
        public String evaluate(RosettaModelObject input) {
            return "Enriched" + input;
        }
    }

    static class Report_Type_2ToType_3 implements RosettaFunction {
    }

    static class Projection_Type_3ToType_4 implements RosettaFunction {
    }
}
