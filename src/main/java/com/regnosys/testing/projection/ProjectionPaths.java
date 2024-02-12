package com.regnosys.testing.projection;

import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.rosetta.model.lib.ModelReportId;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ProjectionPaths {

    public static final Path PROJECTION_PATH = Paths.get("projection");
    public static final String PROJECTION_EXPECTATIONS_FILE_NAME = "projection-expectations.json";

    /**
     * Projection input file is the reports output file (loaded from reports output directory).
     */
    public static Path getProjectionDataItemInputPath(ModelReportId reportIdentifier, String dataSetName, Path reportDataItemInputPath) {
        Path reportOutputPath = RegReportPaths.getDefault().getOutputRelativePath();
        return RegReportPaths.getReportExpectationFilePath(reportOutputPath, reportIdentifier, dataSetName, reportDataItemInputPath);
    }

    public static Path getProjectionDataItemKeyValuePath(Path projectionDataSetPath, Path projectionDataItemInputPath) {
        String projectionDataItemOutputPath = toKeyValueFileExt(projectionDataItemInputPath.getFileName().toString());
        return projectionDataSetPath.resolve(projectionDataItemOutputPath);
    }

    public static Path getProjectionDataItemOutputPath(Path projectionDataSetPath, Path projectionDataItemInputPath) {
        String projectionDataItemOutputPath = toOutputFileExt(projectionDataItemInputPath.getFileName().toString());
        return projectionDataSetPath.resolve(projectionDataItemOutputPath);
    }

    private static String toOutputFileExt(String path) {
        return path.substring(0, path.lastIndexOf(".")) + "-output.xml";
    }

    private static String toKeyValueFileExt(String path) {
        return path.substring(0, path.lastIndexOf(".")) + "-key-value.json";
    }
}
