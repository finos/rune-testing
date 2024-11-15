package com.regnosys.testing.testpack;

/*-
 * ===============
 * Rosetta Testing
 * ===============
 * Copyright (C) 2022 - 2024 REGnosys
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.hashing.ReferenceResolverProcessStep;
import com.regnosys.rosetta.common.util.Pair;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Function;

import static com.regnosys.rosetta.common.transform.TestPackModel.SampleModel.Assertions;
import static com.regnosys.rosetta.common.transform.TestPackUtils.readFile;

public class TestPackFunctionRunnerImpl<IN extends RosettaModelObject> implements TestPackFunctionRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestPackFunctionRunnerImpl.class);
    public static final Path ROSETTA_SOURCE_PATH = Path.of("rosetta-source/src/main/resources/");

    private final Function<IN, RosettaModelObject> function;
    private final Class<IN> inputType;
    private final RosettaTypeValidator typeValidator;
    private final ReferenceConfig referenceConfig;
    private final ObjectMapper inputObjectMapper;
    private final ObjectWriter outputObjectWriter;
    private final Validator xsdValidator;


    public TestPackFunctionRunnerImpl(Function<IN, RosettaModelObject> function,
                                      Class<IN> inputType,
                                      RosettaTypeValidator typeValidator,
                                      ReferenceConfig referenceConfig,
                                      ObjectMapper inputObjectMapper,
                                      ObjectWriter outputObjectWriter,
                                      Validator xsdValidator) {
        this.function = function;
        this.inputType = inputType;
        this.typeValidator = typeValidator;
        this.referenceConfig = referenceConfig;
        this.inputObjectMapper = inputObjectMapper;
        this.outputObjectWriter = outputObjectWriter;
        this.xsdValidator = xsdValidator;
    }

    @Override
    public Pair<String, Assertions> run(Path inputPath) {
        RosettaModelObject output;
        try {
            // TODO - fix this hack.
            Path inputPathFromRepositoryRoot = inputPath.isAbsolute() ? inputPath : ROSETTA_SOURCE_PATH.resolve(inputPath);
            URL inputFileUrl = inputPathFromRepositoryRoot.toUri().toURL();
            IN input = readFile(inputFileUrl, inputObjectMapper, inputType);
            output = function.apply(resolveReferences(input));
        } catch (MalformedURLException e) {
            LOGGER.error("Failed to load input path {}", inputPath, e);
            return Pair.of("", new Assertions(null, null, true));
        } catch (Exception e) {
            LOGGER.error("Exception occurred running sample creation", e);
            return Pair.of("", new Assertions(null, null, true));
        }

        String serialisedOutput;
        try {
            serialisedOutput = outputObjectWriter.writeValueAsString(output);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialise function output", e);
        }

        ValidationReport validationReport = typeValidator.runProcessStep(output.getType(), output);
        validationReport.logReport();
        int actualValidationFailures = validationReport.validationFailures().size();

        Boolean schemaValidationFailure = isSchemaValidationFailure(serialisedOutput);

        Assertions assertions = new Assertions(actualValidationFailures, schemaValidationFailure, false);
        return Pair.of(serialisedOutput, assertions);
    }

    @SuppressWarnings("unchecked")
    private <T extends RosettaModelObject> T resolveReferences(T o) {
        RosettaModelObjectBuilder builder = o.toBuilder();
        new ReferenceResolverProcessStep(referenceConfig).runProcessStep(o.getType(), builder);
        return (T) builder.build();
    }

    private Boolean isSchemaValidationFailure(String xml) {
        if (xsdValidator == null) {
            return null;
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            xsdValidator.validate(new StreamSource(inputStream));
            return true;
        } catch (SAXException e) {
            LOGGER.error("Schema validation failed: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
