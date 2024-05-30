package com.regnosys.testing.testpack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.io.Resources;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Function;

import static com.regnosys.rosetta.common.transform.TestPackModel.SampleModel.Assertions;
import static com.regnosys.rosetta.common.transform.TestPackUtils.readFile;
import static com.regnosys.testing.testpack.TestPackFunctionRunnerProviderImpl.JSON_OBJECT_MAPPER;

class TestPackFunctionRunnerImpl<IN extends RosettaModelObject> implements TestPackFunctionRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestPackFunctionRunnerImpl.class);
    
    private final Function<IN, RosettaModelObject> function;
    private final Class<IN> inputType;
    private final RosettaTypeValidator typeValidator;
    private final ReferenceConfig referenceConfig;
    private final ObjectWriter outputObjectWriter;
    private final Validator xsdValidator;


    public TestPackFunctionRunnerImpl(Function<IN, RosettaModelObject> function,
                                      Class<IN> inputType,
                                      RosettaTypeValidator typeValidator,
                                      ReferenceConfig referenceConfig,
                                      ObjectWriter outputObjectWriter,
                                      Validator xsdValidator) {
        this.function = function;
        this.inputType = inputType;
        this.typeValidator = typeValidator;
        this.referenceConfig = referenceConfig;
        this.outputObjectWriter = outputObjectWriter;
        this.xsdValidator = xsdValidator;
    }

    @Override
    public Pair<String, Assertions> run(Path inputPath) {
        URL inputFileUrl = Resources.getResource(inputPath.toString());
        //assert inputFileUrl != null; // TODO handle nulls (without assert)
        IN input = readFile(inputFileUrl, JSON_OBJECT_MAPPER, inputType);
        RosettaModelObject output;
        try {
            output = function.apply(resolveReferences(input));
        } catch (Exception e) {
            LOGGER.error("Exception occurred running sample creation", e);
            return Pair.of(null, new Assertions(null, null, true));
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
