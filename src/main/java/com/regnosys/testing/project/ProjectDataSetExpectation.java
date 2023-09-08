package com.regnosys.testing.project;

import java.util.List;

public class ProjectDataSetExpectation {
    private final String projectName;
    private final String dataSetName;
    private final List<ProjectDataItemExpectation> dataItemExpectations;

    public ProjectDataSetExpectation(String projectName, String dataSetName, List<ProjectDataItemExpectation> dataItemExpectations) {
        this.projectName = projectName;
        this.dataSetName = dataSetName;
        this.dataItemExpectations = dataItemExpectations;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getDataSetName() {
        return dataSetName;
    }

    public List<ProjectDataItemExpectation> getDataItemExpectations() {
        return dataItemExpectations;
    }
}
