package com.regnosys.testing.project;

import java.util.Objects;

public class ProjectNameAndDataSetName {
    public final String projectName;
    public final String dataSetName;

    public ProjectNameAndDataSetName(String projectName, String dataSetName) {
        this.projectName = projectName;
        this.dataSetName = dataSetName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectNameAndDataSetName that = (ProjectNameAndDataSetName) o;
        return Objects.equals(projectName, that.projectName) && Objects.equals(dataSetName, that.dataSetName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, dataSetName);
    }
}
