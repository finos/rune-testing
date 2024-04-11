package com.regnosys.testing.reports;

public class ReportTestResult {

    private final String inputFileName;
    private final ExpectedAndActual<String> report;
    private final ExpectedAndActual<Integer> validationFailures;

    public ReportTestResult(String inputFileName,
                            ExpectedAndActual<String> report,
                            ExpectedAndActual<Integer> validationFailures) {
        this.inputFileName = inputFileName;
        this.report = report;
        this.validationFailures = validationFailures;
    }

    public String getInputFileName() {
        return inputFileName;
    }


    public ExpectedAndActual<String> getReport() {
        return report;
    }

    public ExpectedAndActual<Integer> getValidationFailures() {
        return validationFailures;
    }
}
