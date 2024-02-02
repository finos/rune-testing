package com.regnosys.testing.projection;

import com.regnosys.testing.reports.ExpectedAndActual;

public class ProjectionTestResult {
    private final String inputFileName;
    private final String outputFileName;
    private final ExpectedAndActual<String> keyValue;
    private final ExpectedAndActual<String> report;
    private final ExpectedAndActual<Integer> validationFailures;
    private final ExpectedAndActual<Boolean> validXml;


    public ProjectionTestResult(String inputFileName,
                                String outputFileName,
                                ExpectedAndActual<String> keyValue, ExpectedAndActual<String> report,
                                ExpectedAndActual<Integer> validationFailures,
                                ExpectedAndActual<Boolean> validXml) {
        this.inputFileName = inputFileName;
        this.outputFileName = outputFileName;
        this.keyValue = keyValue;
        this.report = report;
        this.validationFailures = validationFailures;
        this.validXml = validXml;
    }

    public String getInputFileName() {
        return inputFileName;
    }

    public String getOutputFileName() {
        return outputFileName;
    }

    public ExpectedAndActual<String> getReport() {
        return report;
    }

    public ExpectedAndActual<Integer> getValidationFailures() {
        return validationFailures;
    }

    public ExpectedAndActual<Boolean> getValidXml() {
        return validXml;
    }

    public ExpectedAndActual<String> getKeyValue() {
        return keyValue;
    }
}
