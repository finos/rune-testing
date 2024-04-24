package com.regnosys.testing.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapperCreator;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.testing.TestingExpectationUtil;
import com.regnosys.testing.projection.ProjectionTestExtension;
import com.regnosys.testing.reports.ReportTestExtension;
import com.rosetta.model.lib.RosettaModelObject;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public abstract class TransformTestExtension <T extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback  {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportTestExtension.class);
    private final Module runtimeModule;
    private final Class<T> inputType;
    private Path rootExpectationsPath;
    private String testPackFileName;
    @Inject
    private RosettaTypeValidator typeValidator;
    @Inject
    private ReferenceConfig referenceConfig;
    private Multimap<TestPackAndDataSetName, TransformTestResult> actualExpectation;
    private String regBody;

    protected abstract void writeExpectations(Multimap<TestPackAndDataSetName, TransformTestResult> actualExpectation) throws Exception;

    public TransformTestExtension(Module runtimeModule, Class<T> inputType) {
        this.runtimeModule = runtimeModule;
        this.inputType = inputType;
    }

    public TransformTestExtension<T> withRootExpectationsPath(Path rootExpectationsPath) {
        this.rootExpectationsPath = rootExpectationsPath;
        return this;
    }

    public TransformTestExtension<T> withTestPackFileName(String testPackFileName) {
        this.testPackFileName = testPackFileName;
        return this;
    }


    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        writeExpectations(actualExpectation);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        Guice.createInjector(runtimeModule).injectMembers(this);
        actualExpectation = ArrayListMultimap.create();
    }


    private static URL getInputFileUrl(String inputFile) {
        try {
            return Resources.getResource(inputFile);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Failed to load input file " + inputFile);
            return null;
        }
    }
}
