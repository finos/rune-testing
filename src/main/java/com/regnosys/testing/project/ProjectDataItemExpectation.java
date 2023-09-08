package com.regnosys.testing.project;

import java.util.Comparator;

public class ProjectDataItemExpectation implements Comparable<ProjectDataItemExpectation> {
    private String inputFile;
    private String outputFile;
    private int validationFailures;

    public ProjectDataItemExpectation(String inputFile, String outputFile, int validationFailures) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.validationFailures = validationFailures;
    }

    public String getInputFile() {
        return inputFile;
    }

    public void setInputFile(String inputFile) {
        this.inputFile = inputFile;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public int getValidationFailures() {
        return validationFailures;
    }

    public void setValidationFailures(int validationFailures) {
        this.validationFailures = validationFailures;
    }

    @Override
    public int compareTo(ProjectDataItemExpectation o) {
        return Comparator.comparing(ProjectDataItemExpectation::getInputFile).compare(this, o);
    }
}
