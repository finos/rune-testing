package com.regnosys.testing.projection;

import com.regnosys.testing.reports.ExpectedAndActual;

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
