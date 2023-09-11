package com.regnosys.testing.project;

import com.regnosys.testing.reports.ExpectedAndActual;

public class ProjectTestResult {
    private final String inputFileName;
    private final String outputFileName;
    private final ExpectedAndActual<String> report;
    private final ExpectedAndActual<Integer> validationFailures;


    public ProjectTestResult(String inputFileName, String outputFileName, ExpectedAndActual<String> report, ExpectedAndActual<Integer> validationFailures) {
        this.inputFileName = inputFileName;
        this.outputFileName = outputFileName;
        this.report = report;
        this.validationFailures = validationFailures;
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
}
