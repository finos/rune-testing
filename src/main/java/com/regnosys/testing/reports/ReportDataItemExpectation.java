package com.regnosys.testing.reports;

import java.util.Comparator;

/**
 * Expectations for a report data-item, e.g. single reportable event to be tested.
 */
public class ReportDataItemExpectation implements Comparable<ReportDataItemExpectation> {

    private String name;
    private String fileName;
    private int validationFailures;

    public ReportDataItemExpectation(String name, String fileName, int validationFailures) {
        this.name = name;
        this.fileName = fileName;
        this.validationFailures = validationFailures;
    }

    private ReportDataItemExpectation() {
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getValidationFailures() {
        return validationFailures;
    }

    public void setValidationFailures(int validationFailures) {
        this.validationFailures = validationFailures;
    }

    @Override
    public int compareTo(ReportDataItemExpectation o) {
        return Comparator.<ReportDataItemExpectation, String>comparing(e -> e.getFileName()).compare(this, o);
    }
}
