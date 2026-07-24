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
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

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
 * ({@value #RUNE_CONFIG_PRESENT_IN_MODEL_KEY}). The marker is looked up once via
 * {@link ClassLoader#getResource(String)} (first on classpath order, matching a model's own {@code tests}
 * module resolving to its own marker ahead of a dependency's), and its answer is authoritative: if it
 * says {@code false}, no config is looked up at all, even if one happens to be reachable via a dependency.
 * If it says {@code true}, the config is resolved relative to the marker's own container (its own jar or
 * exploded classes directory), never via a second classpath-order lookup that could resolve to a
 * different container.
 */
public final class DefaultModelSerialisation {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModelSerialisation.class);
    private static final RuneConfigurationService RUNE_CONFIGURATION_SERVICE = new RuneConfigurationService();

    static final String MODEL_PROPERTIES_PATH = "META-INF/rune/model.properties";
    static final String RUNE_CONFIG_PRESENT_IN_MODEL_KEY = "runeConfigPresentInModel";
    static final String RUNE_MAVEN_PLUGIN_VERSION_KEY = "runeMavenPluginVersion";

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
     * {@code classLoader}, consulting its {@value #MODEL_PROPERTIES_PATH} marker.
     *
     * @throws IllegalStateException if no marker is found on the classpath (this {@code rune-testing}
     *         requires a marker that an older {@code rune-maven-plugin} does not produce), if the marker
     *         declares the config present but neither config file is found in its own container, if an
     *         ancestor's marker declares a newer {@code runeMavenPluginVersion} than the winning marker's
     *         (violating the convention that a child's dsl/bundle version is always ≥ its parent's), or if
     *         the configured format is anything other than {@code JSON}/{@code RUNE_JSON} — the backend
     *         honours any {@link SerializationFormat}, but the test harness only mirrors the two JSON
     *         flavours today, so any other value would otherwise silently fall back to the legacy mapper
     *         and diverge from the runtime default.
     */
    public static DefaultModelSerialisation resolve(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        URL markerUrl = classLoader.getResource(MODEL_PROPERTIES_PATH);
        if (markerUrl == null) {
            throw new IllegalStateException("No " + MODEL_PROPERTIES_PATH + " marker found on the classpath. "
                    + "rune-maven-plugin and rune-testing move together as a pair: this model was built with a "
                    + "rune-maven-plugin version that predates the marker (or the marker's generate execution is "
                    + "missing), while this rune-testing version requires it to be present. Rebuild the model "
                    + "with a matching rune-maven-plugin version.");
        }
        checkPluginVersionConvention(classLoader, markerUrl);
        Properties markerProperties = readProperties(markerUrl);
        boolean configPresent = Boolean.parseBoolean(markerProperties.getProperty(RUNE_CONFIG_PRESENT_IN_MODEL_KEY));
        if (!configPresent) {
            return new DefaultModelSerialisation(RosettaObjectMapper.getNewRosettaObjectMapper(), false);
        }
        URL configUrl = resolveConfigUrlInContainer(markerUrl)
                .orElseThrow(() -> new IllegalStateException("Model config marker at " + markerUrl + " declares "
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
     * Verifies the team convention that a child model's dsl/bundle version is always ≥ its parent's, which
     * is what makes marker presence monotonic in plugin version and rules out a marker-less child sitting
     * under a marked ancestor. Enumerates every marker on the classpath and fails if any *other* one
     * declares a {@value #RUNE_MAVEN_PLUGIN_VERSION_KEY} greater than the winning marker's. A missing or
     * unparseable version on either side skips that comparison rather than failing the build, so a
     * malformed marker cannot block it.
     */
    private static void checkPluginVersionConvention(ClassLoader classLoader, URL winningMarkerUrl) {
        ComparableVersion winningVersion = parseVersion(
                readProperties(winningMarkerUrl).getProperty(RUNE_MAVEN_PLUGIN_VERSION_KEY));
        if (winningVersion == null) {
            return;
        }
        Enumeration<URL> allMarkers;
        try {
            allMarkers = classLoader.getResources(MODEL_PROPERTIES_PATH);
        } catch (IOException e) {
            LOGGER.warn("Failed to enumerate {} markers on the classpath, skipping the plugin version "
                    + "convention check", MODEL_PROPERTIES_PATH, e);
            return;
        }
        while (allMarkers.hasMoreElements()) {
            URL markerUrl = allMarkers.nextElement();
            if (markerUrl.toExternalForm().equals(winningMarkerUrl.toExternalForm())) {
                continue;
            }
            String otherVersionString = readProperties(markerUrl).getProperty(RUNE_MAVEN_PLUGIN_VERSION_KEY);
            ComparableVersion otherVersion = parseVersion(otherVersionString);
            if (otherVersion != null && otherVersion.compareTo(winningVersion) > 0) {
                throw new IllegalStateException("Model config marker at " + markerUrl + " declares "
                        + RUNE_MAVEN_PLUGIN_VERSION_KEY + "=" + otherVersionString + ", newer than the winning "
                        + "marker's " + winningMarkerUrl + " (version " + winningVersion + "). The team "
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
