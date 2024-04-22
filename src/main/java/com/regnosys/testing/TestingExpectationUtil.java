package com.regnosys.testing;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
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

    public static List<URL> readTestPacksFromPath(Path basePath, ClassLoader classLoader, String testPackFileName, String regBody) {
        return ClassPathUtils.findPathsFromClassPath(
                List.of(UrlUtils.toPortableString(basePath)),
                testPackFileName.replaceAll("--","-"+regBody+"-"),
                Optional.empty(),
                classLoader
        ).stream()
                .map(UrlUtils::toUrl)
                .collect(Collectors.toList());
    }


    public static URL readPipelineFromPath(Path basePath, ClassLoader classLoader, String pipelineFileName, String regBody) {
        return ClassPathUtils.findPathsFromClassPath(
                        List.of(UrlUtils.toPortableString(basePath)),
                        pipelineFileName.replaceAll("--","-"+regBody+"-"),
                        Optional.empty(),
                        classLoader
                ).stream()
                .map(UrlUtils::toUrl).findFirst().get();
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
