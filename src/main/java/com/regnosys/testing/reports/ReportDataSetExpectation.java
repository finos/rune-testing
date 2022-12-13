package com.regnosys.testing.reports;

import java.util.List;

/**
 * Expectations for a report and data-set, e.g. CFTC Part 45, rates.
 */
public class ReportDataSetExpectation {

    private String reportName;
    private String dataSetName;
    private List<ReportDataItemExpectation> dataItemExpectations;

    public ReportDataSetExpectation(String reportName, String dataSetName, List<ReportDataItemExpectation> dataItemExpectations) {
        this.reportName = reportName;
        this.dataSetName = dataSetName;
        this.dataItemExpectations = dataItemExpectations;
    }

    private ReportDataSetExpectation() {
    }

    public String getReportName() {
        return reportName;
    }

    public String getDataSetName() {
        return dataSetName;
    }

    public List<ReportDataItemExpectation> getDataItemExpectations() {
        return dataItemExpectations;
    }
}
