package com.regnosys.testing.reports;

import com.regnosys.rosetta.common.reports.RegReportIdentifier;

import java.util.Objects;

public class ReportIdentifierAndDataSetName {
    public final RegReportIdentifier reportIdentifier;
    public final String dataSetName;

    public ReportIdentifierAndDataSetName(RegReportIdentifier reportIdentifier, String dataSetName) {
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
