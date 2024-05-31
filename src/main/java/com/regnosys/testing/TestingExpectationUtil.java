package com.regnosys.testing;

/*-
 * ==============
 * Rosetta Testing
 * ==============
 * Copyright (C) 2022 - 2024 REGnosys
 * ==============
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==============
 */

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
    private static final Logger LOGGER = LoggerFactory.getLogger(TestingExpectationUtil.class);

    public static boolean WRITE_EXPECTATIONS = Optional.ofNullable(System.getenv("WRITE_EXPECTATIONS"))
            .map(Boolean::parseBoolean).orElse(false);
    public static boolean CREATE_EXPECTATION_FILES = Optional.ofNullable(System.getenv("CREATE_EXPECTATION_FILES"))
            .map(Boolean::parseBoolean).orElse(false);
    public static Optional<Path> TEST_WRITE_BASE_PATH = Optional.ofNullable(System.getenv("TEST_WRITE_BASE_PATH"))
            .map(Paths::get);

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
