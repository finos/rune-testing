package com.regnosys.testing.projection;

import java.nio.file.Path;
import java.util.Objects;

public class ProjectionNameAndDataSetName {
    public final String projectionName;
    public final String dataSetName;
    public final Path projectExpectationFilePath;

    public ProjectionNameAndDataSetName(String projectionName, String dataSetName, Path projectionExpectationFilePath) {
        this.projectionName = projectionName;
        this.dataSetName = dataSetName;
        this.projectExpectationFilePath = projectionExpectationFilePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectionNameAndDataSetName that = (ProjectionNameAndDataSetName) o;
        return Objects.equals(projectionName, that.projectionName) && Objects.equals(dataSetName, that.dataSetName) && Objects.equals(projectExpectationFilePath, that.projectExpectationFilePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectionName, dataSetName, projectExpectationFilePath);
    }
}
