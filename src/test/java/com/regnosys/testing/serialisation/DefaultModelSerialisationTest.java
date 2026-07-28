package com.regnosys.testing.serialisation;

/*-
 * ===============
 * Rune Testing
 * ===============
 * Copyright (C) 2022 - 2026 REGnosys
 * ===============
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
 * ===============
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.finos.rune.mapper.RuneJsonObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultModelSerialisationTest {

    private static final String MARKER_RELATIVE_PATH = "META-INF/rune/model.properties";
    private static final String DEFAULT_VERSION = "10.4.0";

    @TempDir
    Path tempDir;

    @Test
    void ruleJsonConfigResolvesToRuneJsonMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void explicitJsonConfigResolvesToLegacyMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: JSON
                """);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void noConfigOnClasspathResolvesToLegacyMapper() throws IOException {
        writeMarker(tempDir, false, DEFAULT_VERSION);
        ClassLoader classLoader = dirClassLoader();

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void configWithoutDefaultSerialisationFormatFieldResolvesToLegacyMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                """);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void malformedYamlResolvesToLegacyMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", "model: [unterminated");

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void configMissingRequiredModelKeyResolvesToLegacyMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", "unrelated: value");

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void legacyConfigFileNameIsHonouredWhenNewNameIsAbsent() throws IOException {
        ClassLoader classLoader = configClassLoader("rosetta-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void newConfigFileNameIsPreferredOverLegacyName() throws IOException {
        Files.writeString(tempDir.resolve("rune-config.yml"), """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("rosetta-config.yml"), """
                model:
                  name: Test Model
                  defaultSerialisationFormat: JSON
                """, StandardCharsets.UTF_8);
        writeMarker(tempDir, true, DEFAULT_VERSION);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(dirClassLoader());

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void createWriterMatchesTheResolvedMapper() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        ObjectWriter writer = resolved.createWriter(true);
        assertNotNull(writer);
        // Pretty-printed (rune-json's own canonical shape: 2-space indent, "\n") and round-trips through
        // the same mapper.
        String written = writer.writeValueAsString(Map.of("a", 1));
        assertTrue(written.contains("\n"));
        assertEquals(1, resolved.getObjectMapper().readTree(written).get("a").asInt());
    }

    @Test
    void legacyMapperHonoursAlphabeticalPropertySorting() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: JSON
                """);

        assertTrue(sortsPropertiesAlphabetically(classLoader, true));
        assertFalse(sortsPropertiesAlphabetically(classLoader, false));
    }

    @Test
    void runeJsonMapperKeepsItsNaturalOrderRegardlessOfTheSortFlag() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """);

        // Rune-json defines its own canonical field order, and the Rosetta runtime emits it unsorted.
        // Alphabetising here would move regenerated expectations away from both, so the flag is ignored.
        assertFalse(sortsPropertiesAlphabetically(classLoader, true));
        assertFalse(sortsPropertiesAlphabetically(classLoader, false));
    }

    /**
     * Writes a bean whose declaration order is the reverse of its alphabetical order, so the emitted
     * property order says which of the two the writer used.
     * <p>
     * Resolves a fresh {@link DefaultModelSerialisation} per call on purpose: Jackson caches a
     * serializer per type on first use, so flipping {@code MapperFeature} on a mapper that has already
     * written that type has no effect.
     */
    private boolean sortsPropertiesAlphabetically(ClassLoader classLoader, boolean sortJsonPropertiesAlphabetically)
            throws JsonProcessingException {
        ObjectWriter writer = DefaultModelSerialisation.resolve(classLoader).createWriter(sortJsonPropertiesAlphabetically);
        String written = writer.writeValueAsString(new UnsortedBean());
        return written.indexOf("\"alpha\"") < written.indexOf("\"zulu\"");
    }

    public static class UnsortedBean {
        @SuppressWarnings("unused")
        public String zulu = "z";
        @SuppressWarnings("unused")
        public String alpha = "a";
    }

    @Test
    void resolveRejectsNullClassLoader() {
        assertThrows(NullPointerException.class, () -> DefaultModelSerialisation.resolve(null));
    }

    @Test
    void nonJsonDefaultFailsLoudlyInsteadOfSilentlyFallingBackToLegacy() throws IOException {
        ClassLoader classLoader = configClassLoader("rune-config.yml", """
                model:
                  name: Test Model
                  defaultSerialisationFormat: XML
                """);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> DefaultModelSerialisation.resolve(classLoader));

        assertTrue(exception.getMessage().contains("XML"));
    }

    @Test
    void markerAbsentThrowsNamingBothArtifacts() throws MalformedURLException {
        ClassLoader classLoader = dirClassLoader();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> DefaultModelSerialisation.resolve(classLoader));

        assertTrue(exception.getMessage().contains("rune-maven-plugin"));
        assertTrue(exception.getMessage().contains("rune-testing"));
    }

    @Test
    void markerFalseIgnoresPresentConfigOnClasspath() throws IOException {
        Files.writeString(tempDir.resolve("rune-config.yml"), """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        writeMarker(tempDir, false, DEFAULT_VERSION);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(dirClassLoader());

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void markerTrueResolvesConfigFromItsOwnContainerAmongTwoContainers() throws IOException {
        Path dependencyContainer = Files.createDirectories(tempDir.resolve("dependency"));
        Path modelContainer = Files.createDirectories(tempDir.resolve("model"));

        // A dependency on the classpath ships its own (differently-formatted, marker-less) config.
        Files.writeString(dependencyContainer.resolve("rune-config.yml"), """
                model:
                  name: Dependency Model
                  defaultSerialisationFormat: JSON
                """, StandardCharsets.UTF_8);

        // This model ships its own marker and its own config, in its own container.
        Files.writeString(modelContainer.resolve("rune-config.yml"), """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        writeMarker(modelContainer, true, DEFAULT_VERSION);

        // The dependency's container comes first in classpath order, adversarially.
        ClassLoader classLoader = containersClassLoader(dependencyContainer, modelContainer);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void ancestorMarkerWithHigherPluginVersionThrows() throws IOException {
        Path childContainer = Files.createDirectories(tempDir.resolve("child"));
        Path ancestorContainer = Files.createDirectories(tempDir.resolve("ancestor"));
        writeMarker(childContainer, false, "10.3.0");
        writeMarker(ancestorContainer, false, "10.4.0");

        ClassLoader classLoader = containersClassLoader(childContainer, ancestorContainer);

        assertThrows(IllegalStateException.class, () -> DefaultModelSerialisation.resolve(classLoader));
    }

    @Test
    void conventionCheckSkippedWhenWinningMarkerHasNoVersion() throws IOException {
        Path childContainer = Files.createDirectories(tempDir.resolve("child"));
        Path ancestorContainer = Files.createDirectories(tempDir.resolve("ancestor"));
        writeMarker(childContainer, false, null);
        writeMarker(ancestorContainer, false, "999.0.0");

        ClassLoader classLoader = containersClassLoader(childContainer, ancestorContainer);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void conventionCheckSkippedWhenAnAncestorMarkerHasNoVersion() throws IOException {
        Path childContainer = Files.createDirectories(tempDir.resolve("child"));
        Path ancestorContainer = Files.createDirectories(tempDir.resolve("ancestor"));
        writeMarker(childContainer, false, "10.3.0");
        writeMarker(ancestorContainer, false, null);

        ClassLoader classLoader = containersClassLoader(childContainer, ancestorContainer);

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(classLoader);

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    // ---- leaf election ----

    @Test
    void leafWinsEvenWhenItsAncestorsMarkerComesFirstOnTheClasspath() throws IOException {
        // The sortpom scenario v1 could not survive: the ancestor's jar is listed before the
        // child's, so its marker enumerates first, yet ancestry still elects the child.
        Path ancestorContainer = Files.createDirectories(tempDir.resolve("ancestor"));
        Path childContainer = Files.createDirectories(tempDir.resolve("child"));
        Files.writeString(ancestorContainer.resolve("rune-config.yml"), """
                model:
                  name: Ancestor Model
                  defaultSerialisationFormat: JSON
                """, StandardCharsets.UTF_8);
        writeAncestryMarker(ancestorContainer, true, DEFAULT_VERSION,
                "org.example:ancestor-java:1.0.0", "org.example:ancestor-parent", "");
        Files.writeString(childContainer.resolve("rune-config.yml"), """
                model:
                  name: Child Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        writeAncestryMarker(childContainer, true, DEFAULT_VERSION,
                "org.example:child-java:2.0.0", "org.example:child-parent", "org.example:ancestor-parent");

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(
                containersClassLoader(ancestorContainer, childContainer));

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void grandparentIsRuledOutByTheIntermediatesMarkerNotTheLeafs() throws IOException {
        // Three-model chain (root -> middle -> leaf), root's marker first on the classpath. The
        // leaf's parentModels name only the middle model; the edge that rules the root out comes
        // from the middle model's own marker - the property direct-parents election relies on.
        Path rootContainer = Files.createDirectories(tempDir.resolve("root"));
        Path middleContainer = Files.createDirectories(tempDir.resolve("middle"));
        Path leafContainer = Files.createDirectories(tempDir.resolve("leaf"));
        Files.writeString(rootContainer.resolve("rune-config.yml"), """
                model:
                  name: Root Model
                  defaultSerialisationFormat: JSON
                """, StandardCharsets.UTF_8);
        writeAncestryMarker(rootContainer, true, DEFAULT_VERSION,
                "org.example:root-java:1.0.0", "org.example:root-parent", "");
        writeAncestryMarker(middleContainer, false, DEFAULT_VERSION,
                "org.example:middle-java:1.0.0", "org.example:middle-parent", "org.example:root-parent");
        Files.writeString(leafContainer.resolve("rune-config.yml"), """
                model:
                  name: Leaf Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        writeAncestryMarker(leafContainer, true, DEFAULT_VERSION,
                "org.example:leaf-java:1.0.0", "org.example:leaf-parent", "org.example:middle-parent");

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(
                containersClassLoader(rootContainer, middleContainer, leafContainer));

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void twoIndependentLeavesThrowNamingBothModels() throws IOException {
        Path firstContainer = Files.createDirectories(tempDir.resolve("first"));
        Path secondContainer = Files.createDirectories(tempDir.resolve("second"));
        writeAncestryMarker(firstContainer, false, DEFAULT_VERSION,
                "org.example:first-java:1.0.0", "org.example:first-parent", "");
        writeAncestryMarker(secondContainer, false, DEFAULT_VERSION,
                "org.example:second-java:1.0.0", "org.example:second-parent", "");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> DefaultModelSerialisation.resolve(containersClassLoader(firstContainer, secondContainer)));

        assertTrue(exception.getMessage().contains("org.example:first-java:1.0.0"));
        assertTrue(exception.getMessage().contains("org.example:second-java:1.0.0"));
    }

    @Test
    void anyPreAncestryMarkerDisablesElectionAndFallsBackToClasspathOrder() throws IOException {
        // The first marker is a v1 one (no modelId); by ancestry the second would win, but the
        // compatibility gate keeps classpath order, so the v1 marker's answer (no config) sticks.
        Path v1Container = Files.createDirectories(tempDir.resolve("v1"));
        Path childContainer = Files.createDirectories(tempDir.resolve("child"));
        writeMarker(v1Container, false, DEFAULT_VERSION);
        Files.writeString(childContainer.resolve("rune-config.yml"), """
                model:
                  name: Child Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        writeAncestryMarker(childContainer, true, DEFAULT_VERSION,
                "org.example:child-java:2.0.0", "org.example:child-parent", "");

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(
                containersClassLoader(v1Container, childContainer));

        assertFalse(resolved.getObjectMapper() instanceof RuneJsonObjectMapper);
    }

    @Test
    void ancestryCycleFallsBackToClasspathOrder() throws IOException {
        // Zero leaves - only possible with corrupted markers; must not block the build.
        Path firstContainer = Files.createDirectories(tempDir.resolve("first"));
        Path secondContainer = Files.createDirectories(tempDir.resolve("second"));
        Files.writeString(firstContainer.resolve("rune-config.yml"), """
                model:
                  name: First Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        writeAncestryMarker(firstContainer, true, DEFAULT_VERSION,
                "org.example:first-java:1.0.0", "org.example:first-parent", "org.example:second-parent");
        writeAncestryMarker(secondContainer, false, DEFAULT_VERSION,
                "org.example:second-java:1.0.0", "org.example:second-parent", "org.example:first-parent");

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(
                containersClassLoader(firstContainer, secondContainer));

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void singleRootModelMarkerWinsTrivially() throws IOException {
        Files.writeString(tempDir.resolve("rune-config.yml"), """
                model:
                  name: Root Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        writeAncestryMarker(tempDir, true, DEFAULT_VERSION,
                "com.regnosys.rune-fpml:rosetta-source:1.2.3", "com.regnosys.rune-fpml:parent", "");

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(dirClassLoader());

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void versionConventionCheckStillFiresUnderLeafElection() throws IOException {
        // The elected leaf (the child) has an older plugin version than its ancestor - orthogonal
        // to election, this must still throw, naming the models rather than URLs.
        Path ancestorContainer = Files.createDirectories(tempDir.resolve("ancestor"));
        Path childContainer = Files.createDirectories(tempDir.resolve("child"));
        writeAncestryMarker(ancestorContainer, false, "10.4.0",
                "org.example:ancestor-java:1.0.0", "org.example:ancestor-parent", "");
        writeAncestryMarker(childContainer, false, "10.3.0",
                "org.example:child-java:2.0.0", "org.example:child-parent", "org.example:ancestor-parent");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> DefaultModelSerialisation.resolve(containersClassLoader(ancestorContainer, childContainer)));

        assertTrue(exception.getMessage().contains("org.example:ancestor-java:1.0.0"));
        assertTrue(exception.getMessage().contains("org.example:child-java:2.0.0"));
    }

    @Test
    void duplicateMarkersOfTheSameModelAreOneLeafNotAnAmbiguity() throws IOException {
        // The same model appearing twice on the classpath is not two independent model graphs;
        // classpath order breaks the tie.
        Path firstContainer = Files.createDirectories(tempDir.resolve("first"));
        Path secondContainer = Files.createDirectories(tempDir.resolve("second"));
        Files.writeString(firstContainer.resolve("rune-config.yml"), """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        writeAncestryMarker(firstContainer, true, DEFAULT_VERSION,
                "org.example:model-java:1.0.0", "org.example:model-parent", "");
        writeAncestryMarker(secondContainer, false, DEFAULT_VERSION,
                "org.example:model-java:1.0.0", "org.example:model-parent", "");

        DefaultModelSerialisation resolved = DefaultModelSerialisation.resolve(
                containersClassLoader(firstContainer, secondContainer));

        assertInstanceOf(RuneJsonObjectMapper.class, resolved.getObjectMapper());
    }

    @Test
    void markerEnumerationFailureThrowsInsteadOfFallingBackToClasspathOrder() throws IOException {
        // A readable marker declaring RUNE_JSON is reachable via the single-resource lookup, so a
        // fallback to classpath order would resolve off it and silently adopt its format. An unreadable
        // classpath must fail loudly instead: degrading here reinstates the very classpath-order
        // semantics the marker replaces, in the place it is hardest to spot.
        Files.writeString(tempDir.resolve("rune-config.yml"), """
                model:
                  name: Test Model
                  defaultSerialisationFormat: RUNE_JSON
                """, StandardCharsets.UTF_8);
        writeAncestryMarker(tempDir, true, DEFAULT_VERSION,
                "org.example:test-model-java:1.0.0", "org.example:test-model-parent", "");
        ClassLoader classLoader = new EnumerationFailingClassLoader(dirClassLoader());
        assertNotNull(classLoader.getResource(MARKER_RELATIVE_PATH), "fallback lookup must be able to succeed");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> DefaultModelSerialisation.resolve(classLoader));

        assertTrue(exception.getMessage().contains(MARKER_RELATIVE_PATH));
        assertInstanceOf(IOException.class, exception.getCause());
    }

    // ---- fixtures ----

    /**
     * A classloader whose marker <em>enumeration</em> fails while its single-resource lookup still
     * succeeds — the one shape in which a fallback to {@link ClassLoader#getResource(String)} would have
     * resolved a marker rather than surfacing the IO failure.
     */
    private static final class EnumerationFailingClassLoader extends ClassLoader {

        private final ClassLoader delegate;

        private EnumerationFailingClassLoader(ClassLoader delegate) {
            super(null);
            this.delegate = delegate;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (MARKER_RELATIVE_PATH.equals(name)) {
                throw new IOException("simulated unreadable classpath entry");
            }
            return delegate.getResources(name);
        }

        @Override
        public URL getResource(String name) {
            return delegate.getResource(name);
        }
    }

    private ClassLoader configClassLoader(String fileName, String yaml) throws IOException {
        Files.writeString(tempDir.resolve(fileName), yaml, StandardCharsets.UTF_8);
        writeAncestryMarker(tempDir, true, DEFAULT_VERSION,
                "org.example:test-model-java:1.0.0", "org.example:test-model-parent", "");
        return dirClassLoader();
    }

    private ClassLoader dirClassLoader() throws MalformedURLException {
        return new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
    }

    private ClassLoader containersClassLoader(Path... containers) throws MalformedURLException {
        URL[] urls = new URL[containers.length];
        for (int i = 0; i < containers.length; i++) {
            urls[i] = containers[i].toUri().toURL();
        }
        return new URLClassLoader(urls, null);
    }

    /** A v1 marker, as written by a pre-ancestry rune-maven-plugin: no identity/ancestry keys. */
    private void writeMarker(Path container, boolean configPresent, String runeMavenPluginVersion) throws IOException {
        writeAncestryMarker(container, configPresent, runeMavenPluginVersion, null, null, null);
    }

    private void writeAncestryMarker(Path container, boolean configPresent, String runeMavenPluginVersion,
            String modelSourceGav, String modelId, String parentModels) throws IOException {
        Path marker = container.resolve(MARKER_RELATIVE_PATH);
        Files.createDirectories(marker.getParent());
        Properties properties = new Properties();
        properties.setProperty("runeConfigPresentInModel", String.valueOf(configPresent));
        if (runeMavenPluginVersion != null) {
            properties.setProperty("runeMavenPluginVersion", runeMavenPluginVersion);
        }
        if (modelSourceGav != null) {
            properties.setProperty("modelSourceGav", modelSourceGav);
        }
        if (modelId != null) {
            properties.setProperty("modelId", modelId);
        }
        if (parentModels != null) {
            properties.setProperty("parentModels", parentModels);
        }
        try (OutputStream out = Files.newOutputStream(marker)) {
            properties.store(out, null);
        }
    }
}
