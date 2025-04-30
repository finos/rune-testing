package com.regnosys.testing.pipeline;

/*-
 * ===============
 * Rune Testing
 * ===============
 * Copyright (C) 2022 - 2025 REGnosys
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

import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.validation.ValidationReport;

/**
 * Pipeline function execution result.
 */
public class PipelineFunctionResult {
    private final String serialisedOutput;
    private final ValidationReport validationReport;
    private final TestPackModel.SampleModel.Assertions assertions;

    public PipelineFunctionResult(String serialisedOutput, ValidationReport validationReport, TestPackModel.SampleModel.Assertions assertions) {
        this.serialisedOutput = serialisedOutput;
        this.validationReport = validationReport;
        this.assertions = assertions;
    }

    public TestPackModel.SampleModel.Assertions getAssertions() {
        return assertions;
    }

    public String getSerialisedOutput() {
        return serialisedOutput;
    }

    public ValidationReport getValidationReport() {
        return validationReport;
    }
}
