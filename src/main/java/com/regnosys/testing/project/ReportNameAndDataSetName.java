package com.regnosys.testing.project;

import java.util.Objects;

public class ReportNameAndDataSetName {
    public final String reportName;
    public final String dataSetName;

    public ReportNameAndDataSetName(String reportName, String dataSetName) {
        this.reportName = reportName;
        this.dataSetName = dataSetName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReportNameAndDataSetName that = (ReportNameAndDataSetName) o;
        return Objects.equals(reportName, that.reportName) && Objects.equals(dataSetName, that.dataSetName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportName, dataSetName);
    }
}
