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

import com.rosetta.model.lib.functions.RosettaFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FunctionNameHelperTest {

    private final FunctionNameHelper functionNameHelper = new FunctionNameHelper();

    @Test
    void readableId() {
        assertEquals("asic-trade-to-iso20022", functionNameHelper.readableId(Project_ASICTradeReportToIso20022.class));
    }

    @Test
    void readableIdEnrich() {
        assertEquals("asic-trade-to-iso20022", functionNameHelper.readableId(Enrich_ReportableEventToTransactionReportInstruction.class));
    }

    static class Project_ASICTradeReportToIso20022 implements RosettaFunction {

    }

    static class Enrich_ReportableEventToTransactionReportInstruction implements RosettaFunction {

    }
}
