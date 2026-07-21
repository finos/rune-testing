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
import org.finos.rune.mapper.RuneJsonObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 * The model config is looked up as a classpath resource via the supplied {@link ClassLoader} — the model
 * lives on the application/test classpath here, unlike the products side, which reads from a resolved
 * workspace/jar base path. A dependency model on the same classpath may also ship a config file;
 * {@link ClassLoader#getResource(String)} returns the first on classpath order, which for a model's own
 * {@code tests} module is its own resources.
 */
public final class DefaultModelSerialisation {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModelSerialisation.class);
    private static final RuneConfigurationService RUNE_CONFIGURATION_SERVICE = new RuneConfigurationService();
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
            objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, sortJsonPropertiesAlphabetically);
        }
        return objectMapper.writerWithDefaultPrettyPrinter();
    }

    /**
     * Resolves the default mapper for the model whose configuration is discoverable on
     * {@code classLoader}.
     */
    public static DefaultModelSerialisation resolve(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        if (readDefaultSerialisationFormat(classLoader).orElse(null) == SerializationFormat.RUNE_JSON) {
            return new DefaultModelSerialisation(new RuneJsonObjectMapper(classLoader), true);
        }
        return new DefaultModelSerialisation(RosettaObjectMapper.getNewRosettaObjectMapper(), false);
    }

    private static Optional<SerializationFormat> readDefaultSerialisationFormat(ClassLoader classLoader) {
        return findConfigUrl(classLoader).flatMap(DefaultModelSerialisation::readDefaultSerialisationFormat);
    }

    private static Optional<URL> findConfigUrl(ClassLoader classLoader) {
        return CONFIG_FILE_NAMES.stream()
                .map(classLoader::getResource)
                .filter(Objects::nonNull)
                .findFirst();
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
