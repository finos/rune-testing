package com.regnosys.testing.reports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.regnosys.rosetta.common.reports.RegReportIdentifier;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReportExpectationUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportExpectationUtil.class);

    public static boolean WRITE_EXPECTATIONS = Optional.ofNullable(System.getenv("WRITE_EXPECTATIONS"))
            .map(Boolean::parseBoolean).orElse(false);

    public static boolean CREATE_EXPECTATION_FILES = Optional.ofNullable(System.getenv("CREATE_EXPECTATION_FILES"))
            .map(Boolean::parseBoolean).orElse(false);

    public static Optional<Path> TEST_WRITE_BASE_PATH = Optional.ofNullable(System.getenv("TEST_WRITE_BASE_PATH"))
            .map(Paths::get);

    public static final Path REGULATORY_REPORTING_PATH = Path.of("regulatory-reporting");
    public static final Path INPUT_PATH = REGULATORY_REPORTING_PATH.resolve("input");
    public static final Path OUTPUT_PATH = REGULATORY_REPORTING_PATH.resolve("output");
    public static final Path CONFIG_PATH = REGULATORY_REPORTING_PATH.resolve("config");

    public static final String REPORT_EXPECTATIONS_FILE_NAME = "report-expectations.json";
    public static final String KEY_VALUE_FILE_NAME_SUFFIX = "-key-value.json";
    public static final String REPORT_FILE_NAME_SUFFIX = "-report.json";

    private final static ObjectWriter EXPECTATIONS_WRITER =
            ObjectMapperGenerator.createWriterMapper().writerWithDefaultPrettyPrinter();
    private final static ObjectWriter ROSETTA_OBJECT_WRITER =
            RosettaObjectMapper
                    .getNewRosettaObjectMapper()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .writerWithDefaultPrettyPrinter();

    public static void writeExpectations(Multimap<ReportIdentifierAndDataSet, ReportTestResult> actualExpectation) throws JsonProcessingException {
        if (!WRITE_EXPECTATIONS) {
            LOGGER.info("WRITE_EXPECTATIONS is set to false, not updating expectations.");
            return;
        }
        for (var entry : actualExpectation.asMap().entrySet()) {
            ReportIdentifierAndDataSet key = entry.getKey();
            RegReportIdentifier reportIdentifier = key.reportIdentifier;
            String dataSetName = key.dataSetName;

            Collection<ReportTestResult> reportTestExpectations = entry.getValue();
            List<ReportDataItemExpectation> dataItemExpectations = reportTestExpectations.stream()
                    .map(x -> new ReportDataItemExpectation(x.getName(), x.getInputFileName(), x.getValidationFailures().getActual()))
                    .sorted()
                    .collect(Collectors.toList());
            ReportDataSetExpectation reportDataSetExpectation = new ReportDataSetExpectation(reportIdentifier.getName(), dataSetName, dataItemExpectations);
            String expectationFileContent = EXPECTATIONS_WRITER.writeValueAsString(reportDataSetExpectation);

            // Add environment variable TEST_WRITE_BASE_PATH to override the base write path, e.g.
            // TEST_WRITE_BASE_PATH=/Users/hugohills/code/src/github.com/REGnosys/rosetta-cdm/src/main/resources/
            TEST_WRITE_BASE_PATH
                    .filter(Files::exists)
                    .ifPresent(writeBasePath -> {
                        // 1. write new expectations file
                        Path expectationFileWritePath = writeBasePath.resolve(getReportExpectationsFilePath(reportIdentifier, dataSetName));
                        writeFile(expectationFileWritePath, expectationFileContent, CREATE_EXPECTATION_FILES);

                        // 2. write new key-value json
                        reportTestExpectations.stream()
                                .map(ReportTestResult::getKeyValue)
                                .forEach(r -> writeFile(writeBasePath.resolve(r.getExpectationPath()), r.getActual(), CREATE_EXPECTATION_FILES));

                        // 3. write new report json
                        reportTestExpectations.stream()
                                .map(ReportTestResult::getReport)
                                .forEach(r -> writeFile(writeBasePath.resolve(r.getExpectationPath()), r.getActual(), CREATE_EXPECTATION_FILES));
                    });
        }
    }

    public static Path getOutputReportPath(String reportIdentifierName) {
        return OUTPUT_PATH.resolve(directoryName(reportIdentifierName));
    }

    public static Path getOutputReportDataSetPath(RegReportIdentifier reportIdentifier, String dataSetName) {
        return getOutputReportPath(reportIdentifier.getName()).resolve(directoryName(dataSetName));
    }

    public static Path getReportExpectationsFilePath(RegReportIdentifier reportIdentifier, String dataSetName) {
        return getOutputReportDataSetPath(reportIdentifier, dataSetName).resolve(REPORT_EXPECTATIONS_FILE_NAME);
    }

    public static Path getKeyValueExpectationFilePath(RegReportIdentifier reportIdentifier, String dataSetName, Path inputPath) {
        return getOutputReportDataSetPath(reportIdentifier, dataSetName)
                .resolve(inputPath.getFileName().toString().replace(".json", KEY_VALUE_FILE_NAME_SUFFIX));
    }

    public static Path getReportExpectationFilePath(RegReportIdentifier reportIdentifier, String dataSetName, Path inputPath) {
        return getOutputReportDataSetPath(reportIdentifier, dataSetName)
                .resolve(inputPath.getFileName().toString().replace(".json", REPORT_FILE_NAME_SUFFIX));
    }

    public static List<URL> readReportExpectationsFromPath(Path basePath, ClassLoader classLoader) {
        List<URL> expectations = ClassPathUtils
                .findPathsFromClassPath(List.of(basePath.toString()),
                        REPORT_EXPECTATIONS_FILE_NAME,
                        Optional.empty(),
                        classLoader)
                .stream()
                .map(UrlUtils::toUrl)
                .collect(Collectors.toList());
        return ImmutableList.copyOf(expectations);
    }

    public static ExpectedAndActual<String> getExpectedAndActual(Path expectationPath, Object result) throws IOException {
        String actualJson = ROSETTA_OBJECT_WRITER.writeValueAsString(result);
        String expectedJson = readStringFromResources(expectationPath);
        return new ExpectedAndActual<>(expectationPath, expectedJson, actualJson);
    }

    private static String readStringFromResources(Path resourcePath) {
        return Optional.ofNullable(ClassPathUtils.getResource(resourcePath))
                .map(UrlUtils::toPath)
                .map(ReportExpectationUtil::readString)
                .orElse(null);
    }

    private static String readString(Path fullPath) {
        try {
            return Files.exists(fullPath) ? Files.readString(fullPath) : null;
        } catch (IOException e) {
            LOGGER.error("Failed to read path {}", fullPath, e);
            return null;
        }
    }

    private static String directoryName(String name) {
        return name
                .replace(" ", "-")
                .replace("_", "-")
                .trim().toLowerCase();
    }

    private static void writeFile(Path writePath, String json, boolean create) {
        try {
            if (create) {
                Files.createDirectories(writePath.getParent());
            }
            if (create || Files.exists(writePath)) {
                Files.write(writePath, json.getBytes());
                LOGGER.info("Wrote output to {}", writePath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write output to {}", writePath, e);
        }
    }

    public static class ReportIdentifierAndDataSet {
        private final RegReportIdentifier reportIdentifier;
        private final String dataSetName;

        public ReportIdentifierAndDataSet(RegReportIdentifier reportIdentifier, String dataSetName) {
            this.reportIdentifier = reportIdentifier;
            this.dataSetName = dataSetName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReportIdentifierAndDataSet key = (ReportIdentifierAndDataSet) o;
            return Objects.equals(reportIdentifier, key.reportIdentifier) && Objects.equals(dataSetName, key.dataSetName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reportIdentifier, dataSetName);
        }
    }
}
