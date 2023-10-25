package com.regnosys.testing.reports;

import com.rosetta.model.lib.ModelReportId;

import java.util.Objects;

public class ReportIdentifierAndDataSetName {
    public final ModelReportId reportIdentifier;
    public final String dataSetName;

    public ReportIdentifierAndDataSetName(ModelReportId reportIdentifier, String dataSetName) {
        this.reportIdentifier = reportIdentifier;
        this.dataSetName = dataSetName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReportIdentifierAndDataSetName key = (ReportIdentifierAndDataSetName) o;
        return Objects.equals(reportIdentifier, key.reportIdentifier) && Objects.equals(dataSetName, key.dataSetName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportIdentifier, dataSetName);
    }
}
