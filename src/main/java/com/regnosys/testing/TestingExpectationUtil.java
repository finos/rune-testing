package com.regnosys.testing;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapperCreator;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.testing.reports.ExpectedAndActual;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestingExpectationUtil {
    public final static ObjectWriter EXPECTATIONS_WRITER =
            ObjectMapperGenerator.createWriterMapper().writerWithDefaultPrettyPrinter();
    private static final Logger LOGGER = LoggerFactory.getLogger(TestingExpectationUtil.class);
    public final static ObjectWriter ROSETTA_OBJECT_WRITER =
            RosettaObjectMapper
                    .getNewRosettaObjectMapper()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .writerWithDefaultPrettyPrinter();
    public static boolean WRITE_EXPECTATIONS = Optional.ofNullable(System.getenv("WRITE_EXPECTATIONS"))
            .map(Boolean::parseBoolean).orElse(false);
    public static boolean CREATE_EXPECTATION_FILES = Optional.ofNullable(System.getenv("CREATE_EXPECTATION_FILES"))
            .map(Boolean::parseBoolean).orElse(false);
    public static Optional<Path> TEST_WRITE_BASE_PATH = Optional.ofNullable(System.getenv("TEST_WRITE_BASE_PATH"))
            .map(Paths::get);

    public static List<URL> readExpectationsFromPath(Path basePath, ClassLoader classLoader, String expectationsFileName) {
        List<URL> expectations = ClassPathUtils
                .findPathsFromClassPath(List.of(UrlUtils.toPortableString(basePath)),
                        expectationsFileName,
                        Optional.empty(),
                        classLoader)
                .stream()
                .map(UrlUtils::toUrl)
                .collect(Collectors.toList());
        return ImmutableList.copyOf(expectations);
    }

    public static List<URL> readTestPacksFromPath(Path basePath, ClassLoader classLoader, String regBody) {
        return ClassPathUtils.findPathsFromClassPath(
                        List.of(UrlUtils.toPortableString(basePath)),
                        getProjectionTestPackName(regBody),
                        Optional.empty(),
                        classLoader
                ).stream()
                .map(UrlUtils::toUrl)
                .collect(Collectors.toList());
    }

    public static String getProjectionTestPackName(String regBody) {
        return "test-pack-projection-" + regBody + "-report-to-iso20022.*\\.json";
    }

    public static URL readPipelineFromPath(Path basePath, ClassLoader classLoader, String regBody) {
        return ClassPathUtils.findPathsFromClassPath(
                        List.of(UrlUtils.toPortableString(basePath)),
                        getProjectionPipelineName(regBody),
                        Optional.empty(),
                        classLoader
                ).stream()
                .map(UrlUtils::toUrl).findFirst().get();
    }

    public static String getProjectionPipelineName(String regBody) {
        return "pipeline-projection-" + regBody + "-report-to-iso20022.json";
    }


    public static <T> T readFile(URL u, ObjectMapper mapper, Class<T> clazz) {
        try {
            return mapper.readValue(UrlUtils.openURL(u), clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String readStringFromResources(Path resourcePath) {
        return Optional.ofNullable(ClassPathUtils.getResource(resourcePath))
                .map(UrlUtils::toPath)
                .map(TestingExpectationUtil::readString)
                .orElse(null);
    }

    public static String readString(Path fullPath) {
        try {
            return Files.exists(fullPath) ? Files.readString(fullPath) : null;
        } catch (IOException e) {
            LOGGER.error("Failed to read path {}", fullPath, e);
            return null;
        }
    }

    public static ExpectedAndActual<String> getJsonExpectedAndActual(Path expectationPath, Object jsonResult) throws IOException {
        String actualJson = ROSETTA_OBJECT_WRITER.writeValueAsString(jsonResult);
        String expectedJson = readStringFromResources(expectationPath);
        return new ExpectedAndActual<>(expectationPath, expectedJson, actualJson);
    }

    public static ExpectedAndActual<String> getXmlExpectedAndActual(Path expectationPath, Object xmlResult) throws IOException {
        String actualXML = xmlResult != null ?
                ROSETTA_OBJECT_WRITER.writeValueAsString(xmlResult) :
                "";
        String expectedXML = readStringFromResources(expectationPath);
        return new ExpectedAndActual<>(expectationPath, expectedXML, actualXML);
    }

    //This handles both JSON and XMl outputs
    public static ExpectedAndActual<String> getResultExpectedAndActual(Path expectationPath, PipelineModel pipelineModel, Object result, ObjectWriter objectWriter) throws IOException {
        ObjectWriter rosettaObjectWriter = getObjectWriter(pipelineModel, objectWriter); //This returns the correct objectWriter
        String actualResult = result != null ? rosettaObjectWriter.writeValueAsString(result) :
                "";
        String expectedResult = readStringFromResources(expectationPath);
        return new ExpectedAndActual<>(expectationPath, expectedResult, actualResult);
    }

    private static ObjectWriter getObjectWriter(PipelineModel pipelineModel, ObjectWriter objectWriter) throws IOException {
        ObjectMapper mapper;
        if (pipelineModel.getTransform().getType().equals(TransformType.PROJECTION)) {
            //create a new object writer if the initialised one in TTM is null:
            if (objectWriter == null && pipelineModel.getOutputSerialisation() != null) {
                mapper = RosettaObjectMapperCreator.forXML().create();
                if (pipelineModel.getOutputSerialisation().getConfigPath() != null) {
                    String configPath = pipelineModel.getOutputSerialisation().getConfigPath();
                    URL resource = ClassPathUtils.getResource(Path.of(configPath));
                    mapper = RosettaObjectMapperCreator.forXML(resource.openStream()).create();
                }
            } else {
                return objectWriter;
            }
        } else {
            mapper = RosettaObjectMapperCreator.forJSON().create(); // we always create this for reports
        }
        return mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .writerWithDefaultPrettyPrinter();
    }

    public static void assertJsonEquals(String expectedJson, String resultJson) {
        assertEquals(
                normaliseLineEndings(expectedJson),
                normaliseLineEndings(resultJson));
    }

    public static String normaliseLineEndings(String str) {
        return Optional.ofNullable(str)
                .map(s -> s.replace("\r", ""))
                .orElse(null);
    }

    public static void writeFile(Path writePath, String json, boolean create) {
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
}
