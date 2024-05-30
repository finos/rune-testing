package com.regnosys.testing.testpack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Function;

import static com.regnosys.rosetta.common.transform.TestPackModel.SampleModel.Assertions;
import static com.regnosys.rosetta.common.transform.TestPackUtils.getInputFileUrl;
import static com.regnosys.rosetta.common.transform.TestPackUtils.readFile;

class TestPackFunctionRunnerImpl<IN extends RosettaModelObject> implements TestPackFunctionRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestPackFunctionRunnerImpl.class);
    
    private final Function<IN, RosettaModelObject> function;
    private final Class<IN> inputType;
    private final RosettaTypeValidator typeValidator;
    private final ReferenceConfig referenceConfig;
    private final ObjectMapper objectMapper;


    public TestPackFunctionRunnerImpl(Function<IN, RosettaModelObject> function,
                                      Class<IN> inputType,
                                      RosettaTypeValidator typeValidator,
                                      ReferenceConfig referenceConfig,
                                      ObjectMapper objectMapper) {
        this.function = function;
        this.inputType = inputType;
        this.typeValidator = typeValidator;
        this.referenceConfig = referenceConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public Pair<String, Assertions> run(Path inputPath) {
        URL inputFileUrl = getInputFileUrl(inputPath.toString());
        //assert inputFileUrl != null; // TODO handle nulls (without assert)
        IN input = readFile(inputFileUrl, objectMapper, inputType);
        RosettaModelObject output;
        try {
            output = function.apply(resolveReferences(input));
        } catch (Exception e) {
            LOGGER.error("Exception occurred running sample creation", e);
            return Pair.of(null, new Assertions(null, null, true));
        }

        String serialisedOutput;
        try {
            serialisedOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialise function output", e);
        }

        ValidationReport validationReport = typeValidator.runProcessStep(output.getType(), output);
        validationReport.logReport();
        int actualValidationFailures = validationReport.validationFailures().size();

        // TODO schema validation
        
        Assertions assertions = new Assertions(actualValidationFailures, null, false);
        return Pair.of(serialisedOutput, assertions);
    }
    
    private <T extends RosettaModelObject> T resolveReferences(T o) {
        RosettaModelObjectBuilder builder = o.toBuilder();
        new ReferenceResolverProcessStep(referenceConfig).runProcessStep(o.getType(), builder);
        return (T) builder.build();
    }

    //TODO: to be used for projection
    private Boolean isSchemaValidationFailure(URL xsdSchema, String actualXml) throws SAXException {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // required to process xml elements with an maxOccurs greater than 5000 (rather than unbounded)
        schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        Schema schema = schemaFactory.newSchema(xsdSchema);
        Validator xsdValidator = schema.newValidator();
        if (xsdValidator == null) {
            return null;
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(actualXml.getBytes(StandardCharsets.UTF_8))) {
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
