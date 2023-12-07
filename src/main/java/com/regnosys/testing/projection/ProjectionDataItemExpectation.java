package com.regnosys.testing.projection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;

public class ProjectionDataItemExpectation implements Comparable<ProjectionDataItemExpectation> {
    private String inputFile;
    private String outputFile;
    private int validationFailures;
    private boolean validXml;

    @JsonCreator
    public ProjectionDataItemExpectation(@JsonProperty("inputFile") String inputFile,
                                         @JsonProperty("outputFile") String outputFile,
                                         @JsonProperty("validationFailures") int validationFailures,
                                         @JsonProperty("validXml") boolean validXml) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.validationFailures = validationFailures;
        this.validXml = validXml;
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

    public boolean isValidXml() {
        return validXml;
    }

    public void setValidXml(boolean validXml) {
        this.validXml = validXml;
    }

    @Override
    public int compareTo(ProjectionDataItemExpectation o) {
        return Comparator.comparing(ProjectionDataItemExpectation::getInputFile).compare(this, o);
    }
}
