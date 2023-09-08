package com.regnosys.testing.project;

import java.nio.file.Path;
import java.util.Objects;

public class ProjectNameAndDataSetName {
    public final String projectName;
    public final String dataSetName;
    public final Path projectExpectationFilePath;

    public ProjectNameAndDataSetName(String projectName, String dataSetName, Path projectExpectationFilePath) {
        this.projectName = projectName;
        this.dataSetName = dataSetName;
        this.projectExpectationFilePath = projectExpectationFilePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectNameAndDataSetName that = (ProjectNameAndDataSetName) o;
        return Objects.equals(projectName, that.projectName) && Objects.equals(dataSetName, that.dataSetName) && Objects.equals(projectExpectationFilePath, that.projectExpectationFilePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, dataSetName, projectExpectationFilePath);
    }
}
