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

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.config.RuneConfigurationService;
import com.regnosys.rosetta.config.file.RuneConfigurationFileProvider;
import com.rosetta.model.lib.transform.SerializationFormat;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.finos.rune.mapper.RuneJsonObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the default JSON serialisation (mapper + writer) for a transform side that carries no
 * explicit format, from the model's {@code rune-config.yml}/{@code rosetta-config.yml}
 * {@code defaultSerialisationFormat}: rune-json when the model configures {@code RUNE_JSON}, the legacy
 * {@link RosettaObjectMapper} otherwise.
 * <p>
 * Mirrors {@code JarModelInstance.getDefaultJsonMapper()} on the rosetta-products side, so a model's own
 * JUnit transform tests (and the expectation/test-pack generator) resolve the same default the same way
 * as the Rosetta runtime does. A missing config file, an absent field, or a parse failure all fall back
 * to the legacy mapper, so a model that does not opt in keeps today's behaviour unchanged.
 * <p>
 * Which model actually "owns" the config is no longer inferred from classpath resource order — a child
 * model that ships no config of its own could otherwise silently pick up an ancestor's and adopt its
 * default. Instead, {@code rune-maven-plugin} writes a per-model marker,
 * {@value #MODEL_PROPERTIES_PATH}, recording whether that model ships its own config
 * ({@value #RUNE_CONFIG_PRESENT_IN_MODEL_KEY}), the model's repo identity ({@value #MODEL_ID_KEY}) and
 * the closure of its ancestor models' identities ({@value #ANCESTOR_MODELS_KEY}). All markers on the
 * classpath are enumerated and the <em>leaf</em> is elected — the marker whose {@code modelId} no other
 * marker claims among its {@code ancestorModels} — so the winner is computed from the model graph
 * itself, independent of classpath order. If any marker predates the ancestry keys (no
 * {@code modelId}), election is skipped and the first marker in classpath order wins, keeping older
 * model builds working. The winning marker's answer is authoritative: if it says the config is absent,
 * no config is looked up at all, even if one happens to be reachable via a dependency; if present, the
 * config is resolved relative to the marker's own container (its own jar or exploded classes
 * directory), never via a second classpath-order lookup that could resolve to a different container.
 */
public final class DefaultModelSerialisation {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModelSerialisation.class);
    private static final RuneConfigurationService RUNE_CONFIGURATION_SERVICE = new RuneConfigurationService();

    static final String MODEL_PROPERTIES_PATH = "META-INF/rune/model.properties";
    static final String RUNE_CONFIG_PRESENT_IN_MODEL_KEY = "runeConfigPresentInModel";
    static final String RUNE_MAVEN_PLUGIN_VERSION_KEY = "runeMavenPluginVersion";
    static final String MODEL_SOURCE_GAV_KEY = "modelSourceGav";
    static final String MODEL_ID_KEY = "modelId";
    static final String ANCESTOR_MODELS_KEY = "ancestorModels";

    private static final List<String> CONFIG_FILE_NAMES = List.of(
            RuneConfigurationFileProvider.FILE_NAME,
            RuneConfigurationFileProvider.LEGACY_FILE_NAME);

    private final ObjectMapper objectMapper;
    private final boolean runeJson;

    private DefaultModelSerialisation(ObjectMapper objectMapper, boolean runeJson) {
        this.objectMapper = objectMapper;
        this.runeJson = runeJson;
    }

    /** The default {@link ObjectMapper} for this model: rune-json or legacy, per its configured default. */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * A pretty-printing {@link ObjectWriter} over {@link #getObjectMapper()}.
     * <p>
     * {@code sortJsonPropertiesAlphabetically} is honoured on the legacy path only. Rune-json defines its
     * own canonical field order — {@code RuneJsonAnnotationIntrospector} pins the meta properties
     * ({@code @key}, {@code @ref}, …) to the front of every {@code @RuneDataType} and leaves the domain
     * fields in declaration order — and alphabetising the remainder would reorder them away from that
     * shape, and away from what the Rosetta runtime emits for the same pipeline. The flag is therefore
     * ignored rather than applied, so a rune-json model always writes its natural order.
     *
     * @param sortJsonPropertiesAlphabetically whether the legacy mapper sorts properties alphabetically
     */
    public ObjectWriter createWriter(boolean sortJsonPropertiesAlphabetically) {
        if (!runeJson) {
            objectMapper.setConfig(objectMapper.getSerializationConfig()
                    .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, sortJsonPropertiesAlphabetically));
            objectMapper.setConfig(objectMapper.getDeserializationConfig()
                    .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, sortJsonPropertiesAlphabetically));
        }
        return objectMapper.writerWithDefaultPrettyPrinter();
    }

    /**
     * Resolves the default mapper for the model whose configuration is discoverable on
     * {@code classLoader}, consulting its {@value #MODEL_PROPERTIES_PATH} marker. When several models
     * (and hence markers) are on the classpath, the winner is elected by ancestry — the leaf marker,
     * i.e. the one whose {@value #MODEL_ID_KEY} appears in no other marker's
     * {@value #ANCESTOR_MODELS_KEY} — independent of classpath order. If any marker predates the
     * ancestry keys, the first marker in classpath order wins instead (compatibility fallback).
     *
     * @throws IllegalStateException if no marker is found on the classpath (this {@code rune-testing}
     *         requires a marker that an older {@code rune-maven-plugin} does not produce), if leaf
     *         election finds several independent leaves (two unrelated model graphs on one test
     *         classpath is genuinely ambiguous), if the winning marker declares the config present but
     *         neither config file is found in its own container, if another marker declares a newer
     *         {@code runeMavenPluginVersion} than the winning marker's (violating the convention that a
     *         child's dsl/bundle version is always ≥ its parent's), or if the configured format is
     *         anything other than {@code JSON}/{@code RUNE_JSON} — the backend honours any
     *         {@link SerializationFormat}, but the test harness only mirrors the two JSON flavours
     *         today, so any other value would otherwise silently fall back to the legacy mapper and
     *         diverge from the runtime default.
     */
    public static DefaultModelSerialisation resolve(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        List<Marker> markers = readAllMarkers(classLoader);
        if (markers.isEmpty()) {
            throw new IllegalStateException("No " + MODEL_PROPERTIES_PATH + " marker found on the classpath. "
                    + "rune-maven-plugin and rune-testing move together as a pair: this model was built with a "
                    + "rune-maven-plugin version that predates the marker (or the marker's generate execution is "
                    + "missing), while this rune-testing version requires it to be present. Rebuild the model "
                    + "with a matching rune-maven-plugin version.");
        }
        Marker winner = electWinner(markers);
        LOGGER.info("Resolving default serialisation from marker of {} (modelId {})", winner.displayName(),
                winner.modelId() == null ? "unknown" : winner.modelId());
        checkPluginVersionConvention(markers, winner);
        URL markerUrl = winner.url();
        boolean configPresent = Boolean.parseBoolean(winner.properties().getProperty(RUNE_CONFIG_PRESENT_IN_MODEL_KEY));
        if (!configPresent) {
            return new DefaultModelSerialisation(RosettaObjectMapper.getNewRosettaObjectMapper(), false);
        }
        URL configUrl = resolveConfigUrlInContainer(markerUrl)
                .orElseThrow(() -> new IllegalStateException("Model config marker of " + winner.displayName() + " declares "
                        + RUNE_CONFIG_PRESENT_IN_MODEL_KEY + "=true, but neither " + RuneConfigurationFileProvider.FILE_NAME
                        + " nor " + RuneConfigurationFileProvider.LEGACY_FILE_NAME + " was found alongside it in the "
                        + "same container. This indicates a broken build."));
        Optional<SerializationFormat> defaultFormat = readDefaultSerialisationFormat(configUrl);
        if (defaultFormat.isEmpty() || defaultFormat.get() == SerializationFormat.JSON) {
            return new DefaultModelSerialisation(RosettaObjectMapper.getNewRosettaObjectMapper(), false);
        }
        if (defaultFormat.get() == SerializationFormat.RUNE_JSON) {
            return new DefaultModelSerialisation(new RuneJsonObjectMapper(classLoader), true);
        }
        throw new IllegalStateException("Model config on classpath sets defaultSerialisationFormat: "
                + defaultFormat.get() + ", which is not yet supported by the test harness (only JSON and "
                + "RUNE_JSON are) - resolving a default mapper for it would silently diverge from the "
                + "runtime default.");
    }

    /**
     * Resolves the config file relative to the marker's own container: strips {@value #MODEL_PROPERTIES_PATH}
     * off the marker URL and probes {@code rune-config.yml}, then {@code rosetta-config.yml}, alongside it.
     * Works for both {@code jar:file:/…!/} and exploded {@code file:/…/target/classes/} URL forms, since in
     * both the marker's classpath-relative path is a literal suffix of the marker's URL.
     */
    private static Optional<URL> resolveConfigUrlInContainer(URL markerUrl) {
        String markerUrlString = markerUrl.toExternalForm();
        String containerBase = markerUrlString.substring(0, markerUrlString.length() - MODEL_PROPERTIES_PATH.length());
        for (String fileName : CONFIG_FILE_NAMES) {
            String candidateUrlString = containerBase + fileName;
            URL candidate;
            try {
                candidate = new URI(candidateUrlString).toURL();
            } catch (URISyntaxException | MalformedURLException e) {
                // Unexpected: containerBase was itself derived from a URL, so this would mean the classpath
                // entry's URL isn't validly-encoded (e.g. an unencoded space) - log it rather than silently
                // falling through, since that would otherwise look identical to "file not present".
                LOGGER.error("Could not build a valid URI from {}, skipping this candidate config location",
                        candidateUrlString, e);
                continue;
            }
            try (InputStream ignored = candidate.openStream()) {
                return Optional.of(candidate);
            } catch (FileNotFoundException e) {
                if (fileName.equals(RuneConfigurationFileProvider.LEGACY_FILE_NAME)) {
                    LOGGER.error("{} not present either, no config found in this container", candidate);
                } else {
                    LOGGER.warn("{} not present in this container, trying the legacy candidate name", candidate);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to open candidate config location {}, skipping", candidate, e);
            }
        }
        return Optional.empty();
    }

    /**
     * A parsed {@value #MODEL_PROPERTIES_PATH} marker. Markers keep classpath enumeration order, which
     * remains the tie-breaking/fallback order wherever leaf election cannot decide.
     */
    private record Marker(URL url, Properties properties) {

        /** The model's repo identity ({@code groupId:artifactId}); {@code null} on pre-ancestry markers. */
        String modelId() {
            String modelId = properties.getProperty(MODEL_ID_KEY);
            return modelId == null || modelId.isBlank() ? null : modelId.trim();
        }

        /** The identities of all the model's ancestors; empty for a root model or a pre-ancestry marker. */
        Set<String> ancestorModels() {
            String ancestorModels = properties.getProperty(ANCESTOR_MODELS_KEY);
            if (ancestorModels == null || ancestorModels.isBlank()) {
                return Set.of();
            }
            return Arrays.stream(ancestorModels.split(","))
                    .map(String::trim)
                    .filter(ancestor -> !ancestor.isEmpty())
                    .collect(Collectors.toSet());
        }

        /** Names the marker in log/error text: the real artifact when recorded, the URL otherwise. */
        String displayName() {
            String modelSourceGav = properties.getProperty(MODEL_SOURCE_GAV_KEY);
            return modelSourceGav == null || modelSourceGav.isBlank() ? url.toExternalForm() : modelSourceGav.trim();
        }
    }

    /**
     * Enumerates and parses every marker on the classpath, in classpath order. Should enumeration
     * itself fail (an IO problem, not a malformed marker), degrades to the single
     * {@link ClassLoader#getResource(String)} lookup with a warning rather than failing resolution.
     */
    private static List<Marker> readAllMarkers(ClassLoader classLoader) {
        List<URL> urls = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(MODEL_PROPERTIES_PATH);
            while (resources.hasMoreElements()) {
                urls.add(resources.nextElement());
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to enumerate {} markers on the classpath; falling back to the first marker "
                    + "in classpath order", MODEL_PROPERTIES_PATH, e);
            URL single = classLoader.getResource(MODEL_PROPERTIES_PATH);
            if (single != null) {
                urls.add(single);
            }
        }
        return urls.stream().map(url -> new Marker(url, readProperties(url))).collect(Collectors.toList());
    }

    /**
     * Elects the winning marker: the <em>leaf</em> of the model graph, i.e. the marker whose
     * {@code modelId} appears in no other marker's {@code ancestorModels} (exact, case-sensitive GA
     * comparison). Falls back to classpath order when any marker predates the ancestry keys (so models
     * built with a pre-ancestry rune-maven-plugin keep working) and, with a warning, when no leaf
     * exists (an ancestry cycle, only possible via corrupted markers — a malformed marker must not
     * block a build). Several markers agreeing on one {@code modelId} (the same model twice on the
     * classpath) are one leaf, resolved by classpath order; leaves with <em>different</em> identities
     * are two independent model graphs, which is genuinely ambiguous and fails loudly.
     */
    private static Marker electWinner(List<Marker> markers) {
        if (markers.stream().anyMatch(marker -> marker.modelId() == null)) {
            LOGGER.debug("Leaf election skipped: a marker without {} (written by a pre-ancestry "
                    + "rune-maven-plugin) is on the classpath; falling back to the first marker in classpath "
                    + "order", MODEL_ID_KEY);
            return markers.get(0);
        }
        List<Marker> leaves = markers.stream()
                .filter(candidate -> markers.stream()
                        .noneMatch(other -> other != candidate && other.ancestorModels().contains(candidate.modelId())))
                .collect(Collectors.toList());
        if (leaves.isEmpty()) {
            LOGGER.warn("No leaf model found among the {} markers on the classpath - their ancestorModels "
                    + "declarations form a cycle, which can only come from corrupted markers. Falling back to "
                    + "the first marker in classpath order.", MODEL_PROPERTIES_PATH);
            return markers.get(0);
        }
        long distinctLeafIds = leaves.stream().map(Marker::modelId).distinct().count();
        if (distinctLeafIds > 1) {
            String leafNames = leaves.stream().map(Marker::displayName).collect(Collectors.joining(", "));
            throw new IllegalStateException("Multiple independent leaf models found on the test classpath: "
                    + leafNames + ". None of them is an ancestor of another, so there is no single \"model "
                    + "under test\" to resolve the default serialisation for. Remove the unrelated model from "
                    + "the classpath, or declare the missing ancestry via rosetta.parent.* properties in the "
                    + "child model's top-level pom and rebuild it.");
        }
        return leaves.get(0);
    }

    /**
     * Verifies the team convention that a child model's dsl/bundle version is always ≥ its parent's, which
     * is what makes marker presence monotonic in plugin version and rules out a marker-less child sitting
     * under a marked ancestor. Fails if any *other* marker declares a
     * {@value #RUNE_MAVEN_PLUGIN_VERSION_KEY} greater than the winning marker's. A missing or
     * unparseable version on either side skips that comparison rather than failing the build, so a
     * malformed marker cannot block it. Orthogonal to leaf election: this detects version skew, election
     * detects order skew.
     */
    private static void checkPluginVersionConvention(List<Marker> markers, Marker winner) {
        ComparableVersion winningVersion = parseVersion(winner.properties().getProperty(RUNE_MAVEN_PLUGIN_VERSION_KEY));
        if (winningVersion == null) {
            return;
        }
        for (Marker marker : markers) {
            if (marker == winner) {
                continue;
            }
            String otherVersionString = marker.properties().getProperty(RUNE_MAVEN_PLUGIN_VERSION_KEY);
            ComparableVersion otherVersion = parseVersion(otherVersionString);
            if (otherVersion != null && otherVersion.compareTo(winningVersion) > 0) {
                throw new IllegalStateException("Model config marker of " + marker.displayName() + " declares "
                        + RUNE_MAVEN_PLUGIN_VERSION_KEY + "=" + otherVersionString + ", newer than the winning "
                        + "marker's (" + winner.displayName() + ", version " + winningVersion + "). The team "
                        + "convention that a child model's rune dsl/bundle version is always >= its parent's has "
                        + "been violated.");
            }
        }
    }

    private static ComparableVersion parseVersion(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        try {
            return new ComparableVersion(version);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Properties readProperties(URL url) {
        Properties properties = new Properties();
        try (InputStream in = url.openStream()) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read model properties marker at " + url, e);
        }
        return properties;
    }

    private static Optional<SerializationFormat> readDefaultSerialisationFormat(URL configUrl) {
        try {
            return Optional.ofNullable(RUNE_CONFIGURATION_SERVICE.read(configUrl).getModel().getDefaultSerialisationFormat());
        } catch (Exception e) {
            // Catches both IOException (parse failure) and runtime exceptions from RuneConfiguration's
            // validating constructors (e.g. a config missing the required "model" key) - a malformed
            // config must fall back to the legacy mapper rather than fail test setup outright.
            LOGGER.warn("Failed to read model config at {}, falling back to legacy default JSON mapper", configUrl, e);
            return Optional.empty();
        }
    }
}
