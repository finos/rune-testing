package com.regnosys.testing.testpack;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Injector;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.rosetta.model.lib.RosettaModelObject;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Optional;
import java.util.function.Function;

import static com.regnosys.rosetta.common.transform.TestPackUtils.getObjectWriter;

class TestPackFunctionRunnerProviderImpl implements TestPackFunctionRunnerProvider {

    protected static final ObjectMapper JSON_OBJECT_MAPPER = RosettaObjectMapper.getNewRosettaObjectMapper();
    protected final static ObjectWriter JSON_OBJECT_WRITER =
            JSON_OBJECT_MAPPER
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .writerWithDefaultPrettyPrinter();
    
    @Inject
    RosettaTypeValidator typeValidator;
    @Inject
    ReferenceConfig referenceConfig;

    @Override
    public TestPackFunctionRunner create(PipelineModel.Transform transform, Injector injector) {
        Class<? extends RosettaModelObject> inputType = toClass(transform.getInputType());
        Class<?> functionType = toClass(transform.getFunction());
        return createTestPackFunctionRunner(functionType, inputType, injector, JSON_OBJECT_WRITER, null);
    }

    @Override
    public TestPackFunctionRunner create(PipelineModel.Transform transform, PipelineModel.Serialisation outputSerialisation, ImmutableMap<Class<?>, String> functionSchemaMap, Injector injector) {
        Class<? extends RosettaModelObject> inputType = toClass(transform.getInputType());
        Class<?> functionType = toClass(transform.getFunction());
        // Output serialisation
        ObjectWriter outputObjectWriter = getObjectWriter(outputSerialisation).orElse(JSON_OBJECT_WRITER);
        // XSD validation
        Validator xsdValidator = getXsdValidator(functionType, functionSchemaMap);
        return createTestPackFunctionRunner(functionType, inputType, injector, outputObjectWriter, xsdValidator);
    }
    
    private <IN extends RosettaModelObject> TestPackFunctionRunner createTestPackFunctionRunner(Class<?> functionType, Class<IN> inputType, Injector injector, ObjectWriter outputObjectWriter, Validator xsdValidator) {
        Function<IN, RosettaModelObject> transformFunction = getTransformFunction(functionType, inputType, injector);
        return new TestPackFunctionRunnerImpl<>(transformFunction, inputType, typeValidator, referenceConfig, outputObjectWriter, xsdValidator);
    }

    private Class<? extends RosettaModelObject> toClass(String name) {
        try {
            return (Class<? extends RosettaModelObject>) Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private <IN extends RosettaModelObject> Function<IN, RosettaModelObject> getTransformFunction(Class<?> functionType, Class<IN> inputType, Injector injector) {
        Object functionInstance = injector.getInstance(functionType);
        Method evaluateMethod;
        try {
            evaluateMethod = functionInstance.getClass().getMethod("evaluate", inputType);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(String.format("Evaluate method with input type %s not found", inputType.getName()), e);
        }
        return (resolvedInput) -> {
            try {
                return (RosettaModelObject) evaluateMethod.invoke(functionInstance, resolvedInput);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to invoke evaluate method", e);
            }
        };
    }

    private Validator getXsdValidator(Class<?> functionType, ImmutableMap<Class<?>, String> functionSchemaMap) {
        URL schemaUrl = Optional.ofNullable(functionSchemaMap.get(functionType))
                .map(r -> Resources.getResource(r))
                .orElse(null);
        if (schemaUrl == null) {
            return null;
        }
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // required to process xml elements with an maxOccurs greater than 5000 (rather than unbounded)
            schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            Schema schema = schemaFactory.newSchema(schemaUrl);
            return schema.newValidator();
        } catch (SAXException e) {
            throw new RuntimeException(String.format("Failed to create schema validator for {}", schemaUrl),e);
        }
    }
}
