package com.regnosys.testing.project;

import java.util.List;

public class ProjectDataSetExpectation {
    private final String reportName;
    private final String dataSetName;
    private final List<ProjectDataItemExpectation> dataItemExpectations;

    public ProjectDataSetExpectation(String reportName, String dataSetName, List<ProjectDataItemExpectation> dataItemExpectations) {
        this.reportName = reportName;
        this.dataSetName = dataSetName;
        this.dataItemExpectations = dataItemExpectations;
    }

    public String getReportName() {
        return reportName;
    }

    public String getDataSetName() {
        return dataSetName;
    }

    public List<ProjectDataItemExpectation> getDataItemExpectations() {
        return dataItemExpectations;
    }
}
