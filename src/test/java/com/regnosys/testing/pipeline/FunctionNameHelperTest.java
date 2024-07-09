package com.regnosys.testing.pipeline;

import com.rosetta.model.lib.functions.RosettaFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FunctionNameHelperTest {

    private final FunctionNameHelper functionNameHelper = new FunctionNameHelper();

    @Test
    void readableId() {
        assertEquals("asic-trade-to-iso20022", functionNameHelper.readableId(Project_ASICTradeReportToIso20022.class));
    }

    static class Project_ASICTradeReportToIso20022 implements RosettaFunction {

    }
}