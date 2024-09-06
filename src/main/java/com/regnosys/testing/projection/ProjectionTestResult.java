package com.regnosys.testing.projection;

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

import com.regnosys.testing.reports.ExpectedAndActual;

@Deprecated // is this used?
public class ProjectionTestResult {
    private final String inputFileName;
    private final String outputFileName;
    private final ExpectedAndActual<String> output;
    private final ExpectedAndActual<Integer> validationFailures;
    private final ExpectedAndActual<Boolean> validXml;
    private final ExpectedAndActual<Boolean> error;


    public ProjectionTestResult(String inputFileName,
                                String outputFileName,
                                ExpectedAndActual<String> output,
                                ExpectedAndActual<Integer> validationFailures,
                                ExpectedAndActual<Boolean> validXml,
                                ExpectedAndActual<Boolean> error) {
        this.inputFileName = inputFileName;
        this.outputFileName = outputFileName;
        this.output = output;
        this.validationFailures = validationFailures;
        this.validXml = validXml;
        this.error = error;
    }

    public String getInputFileName() {
        return inputFileName;
    }

    public String getOutputFileName() {
        return outputFileName;
    }

    public ExpectedAndActual<String> getOutput() {
        return output;
    }

    public ExpectedAndActual<Integer> getValidationFailures() {
        return validationFailures;
    }

    public ExpectedAndActual<Boolean> getValidXml() {
        return validXml;
    }

    public ExpectedAndActual<Boolean> getError() {
        return error;
    }
}
