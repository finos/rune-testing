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

import static org.junit.Assert.assertTrue;


public class SchemeImporterTestHelper {

    public enum EnumComparison {
        /**
         * Model and coding scheme enum values exactly match
         */
        ExactMatch,
        /**
         * Model contains all coding scheme enum values, however additional values are allowed
         */
        AdditiveMatch
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(SchemeImporterTestHelper.class);
    @Inject
    private SchemeImporter schemeImporter;
    @Inject
    private ModelLoader modelLoader;
    private static final Comparator<RosettaEnumValue> enumValueComparator = Comparator.comparing(RosettaEnumValue::getName)
            .thenComparing(RosettaEnumValue::getDefinition);

    public void checkEnumsAreValid(String rosettaPathRoot, String body, String codingScheme, SchemeEnumReader schemeEnumReader, boolean writeTestOutput, EnumComparison enumComparison) throws IOException {
        URL[] rosettaPaths = getRosettaPaths(rosettaPathRoot);

        List<RosettaModel> models = modelLoader.loadRosettaModels(rosettaPaths);
        List<RosettaEnumeration> rosettaEnumsFromModel = schemeImporter.getRosettaEnumsFromModel(models, body, codingScheme);

        if(writeTestOutput) {
            persistEnumValues(rosettaEnumsFromModel, schemeEnumReader, enumComparison);
        }

        validateEnumValues(rosettaEnumsFromModel, schemeEnumReader, enumComparison);



        //persist enum values
        //if it is exact match
            //Blat the stuff for Exact match and replace from schema enums
        //if it is additive match
            //if coding scheme has extra values, add them alone
        //Build a list of rosetta enum values combinig but removing duplicates

        //loop through coding schme and remove it from model based on the name - there will be a pure list and then we can add the extra ones from coding schmes at the bottom
    }

    private void persistEnumValues(List<RosettaEnumeration> rosettaEnumsFromModel, SchemeEnumReader schemeEnumReader, EnumComparison enumComparison) throws IOException {
        Map<String, String> generatedFromScheme = null;
        if(enumComparison.equals(EnumComparison.ExactMatch)){
            for (RosettaEnumeration rosettaEnumeration : rosettaEnumsFromModel) {
                List<RosettaEnumValue> codingSchemeEnumValues = schemeImporter.getEnumValuesFromCodingScheme(rosettaEnumeration, schemeEnumReader);
                overwriteEnums(rosettaEnumeration, codingSchemeEnumValues);
            }
            generatedFromScheme = schemeImporter.generateRosettaEnums(rosettaEnumsFromModel);
        }
        else if (enumComparison.equals(EnumComparison.AdditiveMatch)){


        }
        assert generatedFromScheme != null;
        writeTestOutput(generatedFromScheme);
    }

    @VisibleForTesting
    boolean compareEnumValues(List<RosettaEnumValue> modelEnumValues, List<RosettaEnumValue> codingSchemeEnumValues, EnumComparison enumComparison) {
        if (enumComparison.equals(EnumComparison.ExactMatch)) {
            return CollectionUtils.listMatch(codingSchemeEnumValues, modelEnumValues, (a, b) -> enumValueComparator.compare(a, b) == 0);
        } else if(enumComparison.equals(EnumComparison.AdditiveMatch)){
            return CollectionUtils.collectionContains(codingSchemeEnumValues, modelEnumValues, (a, b) -> enumValueComparator.compare(a, b) == 0);
        }
        return false;
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

    private void validateEnumValues(List<RosettaEnumeration> rosettaEnumsFromModel, SchemeEnumReader schemeEnumReader, EnumComparison enumComparison) {
        for (RosettaEnumeration rosettaEnumeration : rosettaEnumsFromModel) {
            List<RosettaEnumValue> modelEnumValues = rosettaEnumeration.getEnumValues();
            List<RosettaEnumValue> codingSchemeEnumValues = schemeImporter.getEnumValuesFromCodingScheme(rosettaEnumeration, schemeEnumReader);
            assertTrue("Enum values for " + rosettaEnumeration.getName() + " do not match ", compareEnumValues(modelEnumValues, codingSchemeEnumValues, enumComparison));
        }
    }

    private void overwriteEnums(RosettaEnumeration rosettaEnumeration, List<RosettaEnumValue> newEnumValues) {
        rosettaEnumeration.getEnumValues().clear();
        rosettaEnumeration.getEnumValues().addAll(newEnumValues);
    }
}
