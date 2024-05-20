package com.regnosys.testing.schemeimport;

import com.google.common.annotations.VisibleForTesting;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.CollectionUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.rosetta.RosettaEnumValue;
import com.regnosys.rosetta.rosetta.RosettaEnumeration;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.transgest.ModelLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;


public class SchemeImporterTestHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchemeImporterTestHelper.class);
    @Inject
    private SchemeImporter schemeImporter;
    @Inject
    private ModelLoader modelLoader;
    private static final Comparator<RosettaEnumValue> enumValueComparator = Comparator.comparing(RosettaEnumValue::getName)
            .thenComparing(RosettaEnumValue::getDefinition);

    public void checkEnumsAreValid(String rosettaPathRoot, String body, String codingScheme, SchemeEnumReader schemeEnumReader, boolean writeTestOutput, boolean devMode) throws IOException {
        URL[] rosettaPaths = getRosettaPaths(rosettaPathRoot);

        List<RosettaModel> models = modelLoader.loadRosettaModels(rosettaPaths);
        List<RosettaEnumeration> rosettaEnumsFromModel = schemeImporter.getRosettaEnumsFromModel(models, body, codingScheme);

        validateEnumValues(rosettaEnumsFromModel, body, codingScheme, schemeEnumReader, devMode);
    }

    private void validateEnumValues(List<RosettaEnumeration> rosettaEnumsFromModel, String body, String codingScheme, SchemeEnumReader schemeEnumReader, boolean devMode) {
        for (RosettaEnumeration rosettaEnumeration : rosettaEnumsFromModel) {
            List<RosettaEnumValue> modelEnumValues = rosettaEnumeration.getEnumValues();
            List<RosettaEnumValue> codingSchemeEnumValues = schemeImporter.getEnumValuesFromCodingScheme(rosettaEnumeration, body, codingScheme, schemeEnumReader);
            assertTrue("Enum values for " + rosettaEnumeration.getName() + " do not match ", compareEnumValues(modelEnumValues, codingSchemeEnumValues, devMode));
        }
    }

    @VisibleForTesting
    boolean compareEnumValues(List<RosettaEnumValue> modelEnumValues, List<RosettaEnumValue> codingSchemeEnumValues, boolean devMode) {
        if (devMode) {
            return CollectionUtils.listMatch(codingSchemeEnumValues, modelEnumValues, (a, b) -> enumValueComparator.compare(a, b) == 0);
        } else {
            return CollectionUtils.collectionMatch(codingSchemeEnumValues, modelEnumValues, (a, b) -> enumValueComparator.compare(a, b) == 0);
        }
    }

    protected URL[] getRosettaPaths(String rosettaPathRoot) {
        return ClassPathUtils.findPathsFromClassPath(
                        List.of(rosettaPathRoot),
                        ".*\\.rosetta",
                        Optional.empty(),
                        SchemeImporter.class.getClassLoader()
                ).stream()
                .map(UrlUtils::toUrl)
                .toArray(URL[]::new);
    }

    protected boolean filterNamespace(RosettaModel model, String namespaceIncludeRegex) {
        return Optional.ofNullable(namespaceIncludeRegex)
                .map(regex -> model.getName().matches(regex))
                .orElse(true);
    }

    protected String getContents(URL[] rosettaPaths, String fileName) throws IOException {
        URL rosettaPath = Arrays.stream(rosettaPaths)
                .filter(x -> getFileName(x.getFile()).equals(fileName))
                .findFirst().orElseThrow();
        String contents = new String(rosettaPath.openStream().readAllBytes(), StandardCharsets.UTF_8);
        return RosettaResourceWriter.rewriteProjectVersion(contents);
    }

    protected String getFileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    protected void writeTestOutput(Map<String, String> rosettaExpected) throws IOException {
        // Add environment variable TEST_WRITE_BASE_PATH to override the base write path, e.g.
        // TEST_WRITE_BASE_PATH=/Users/hugohills/code/src/github.com/REGnosys/rosetta-cdm/src/main/rosetta/
        Path basePath = Optional.ofNullable(System.getenv("TEST_WRITE_BASE_PATH"))
                .map(Paths::get)
                .filter(Files::exists)
                .orElseThrow();

        for (String fileName : rosettaExpected.keySet()) {
            Path outputPath = basePath.resolve(fileName);
            Files.writeString(outputPath, rosettaExpected.get(fileName), StandardCharsets.UTF_8);
            LOGGER.info("Wrote test output to {}", outputPath.toAbsolutePath());
        }
    }
}
