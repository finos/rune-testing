package com.regnosys.testing.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ProjectDataSetExpectation {
    private final String projectName;
    private final String dataSetName;
    private final List<ProjectDataItemExpectation> dataItemExpectations;

    @JsonCreator
    public ProjectDataSetExpectation(@JsonProperty("projectName") String projectName,
                                     @JsonProperty("dataSetName") String dataSetName,
                                     @JsonProperty("dataItemExpectations") List<ProjectDataItemExpectation> dataItemExpectations) {
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
