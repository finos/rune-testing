package com.regnosys.testing.pipeline;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.hashing.ReferenceResolverProcessStep;
import com.regnosys.rosetta.common.postprocess.PathCountProcessor;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import com.rosetta.model.lib.path.RosettaPath;
import com.rosetta.model.lib.process.PostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Function;

import static com.regnosys.rosetta.common.transform.TestPackModel.SampleModel.Assertions;
import static com.regnosys.rosetta.common.transform.TestPackUtils.readFile;
import static com.regnosys.testing.transform.TransformTestExtension.ERROR_OUTPUT;

public class PipelineFunctionRunnerImpl<IN extends RosettaModelObject> implements PipelineFunctionRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineFunctionRunnerImpl.class);

    private final TransformType transformType;
    private final Function<IN, RosettaModelObject> function;
    private final Class<IN> inputType;
    private final RosettaTypeValidator typeValidator;
    private final ReferenceConfig referenceConfig;
    private final ObjectMapper inputObjectMapper;
    private final ObjectWriter outputObjectWriter;
    private final PostProcessor postProcessor;
    private final Validator xsdValidator;


    public PipelineFunctionRunnerImpl(TransformType transformType,
                                      Function<IN, RosettaModelObject> function,
                                      Class<IN> inputType,
                                      RosettaTypeValidator typeValidator,
                                      ReferenceConfig referenceConfig,
                                      ObjectMapper inputObjectMapper,
                                      ObjectWriter outputObjectWriter,
                                      PostProcessor postProcessor,
                                      Validator xsdValidator) {
        this.transformType = transformType;
        this.function = function;
        this.inputType = inputType;
        this.typeValidator = typeValidator;
        this.referenceConfig = referenceConfig;
        this.inputObjectMapper = inputObjectMapper;
        this.outputObjectWriter = outputObjectWriter;
        this.postProcessor = postProcessor;
        this.xsdValidator = xsdValidator;
    }

    @Override
    public PipelineFunctionResult run(Path inputPath) {
        Integer inputPathCount = null;
        Integer outputPathCount = null;
        Integer actualValidationFailures = null;
        Boolean schemaValidationFailure = null;
        
        try {
            URL inputFileUrl = inputPath.toUri().toURL();
            IN input = readFile(inputFileUrl, inputObjectMapper, inputType);
            IN resolvedInput = resolveReferences(input);

            RosettaModelObject output = function.apply(resolvedInput);
            RosettaModelObject postProcessedOutput = postProcess(output);

            // serialised output
            String serialisedOutput = outputObjectWriter.writeValueAsString(postProcessedOutput);

            if (transformType == TransformType.TRANSLATE) {
                inputPathCount = getPathCount(input);
                outputPathCount = getPathCount(postProcessedOutput);
            }

            // validation failures
            ValidationReport validationReport = typeValidator.runProcessStep(postProcessedOutput.getType(), postProcessedOutput);
            actualValidationFailures = validationReport.validationFailures().size();

            // schema validation
            schemaValidationFailure = isSchemaValidationFailure(serialisedOutput);

            Assertions assertions =
                    new Assertions(inputPathCount,
                            outputPathCount,
                            actualValidationFailures,
                            schemaValidationFailure,
                            false);
            return new PipelineFunctionResult(serialisedOutput, validationReport, assertions);
        } catch (Exception e) {
            LOGGER.error("Exception occurred running transform", e);
            Assertions assertions = 
                    new Assertions(inputPathCount, 
                            outputPathCount, 
                            actualValidationFailures, 
                            schemaValidationFailure, 
                            true);
            return new PipelineFunctionResult(ERROR_OUTPUT, null, assertions);
        }
    }

    private int getPathCount(RosettaModelObject o) {
        PathCountProcessor processor = new PathCountProcessor();
        o.process(new RosettaPath.NullPath(), processor);
        return processor.report().getCollectedPaths().size();
    }

    private <X extends RosettaModelObject> X postProcess(X output) {
        RosettaModelObjectBuilder outputBuilder = output.toBuilder();
        postProcessor.postProcess(outputBuilder.getType(), outputBuilder);
        //noinspection unchecked
        return (X) outputBuilder.build();
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
