package com.regnosys.testing.project;

import com.rosetta.model.lib.ModelReportId;

import java.util.Objects;

public class ReportIdAndDataSetName {
    public final ModelReportId reportId;
    public final String dataSetName;

    public ReportIdAndDataSetName(ModelReportId reportId, String dataSetName) {
        this.reportId = reportId;
        this.dataSetName = dataSetName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReportIdAndDataSetName that = (ReportIdAndDataSetName) o;
        return Objects.equals(reportId, that.reportId) && Objects.equals(dataSetName, that.dataSetName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportId, dataSetName);
    }
}
