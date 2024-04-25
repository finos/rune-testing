package com.regnosys.testing.transform;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.testing.TestingExpectationUtil;
import com.regnosys.testing.reports.ExpectedAndActual;
import com.regnosys.testing.reports.ReportTestExtension;
import com.rosetta.model.lib.RosettaModelObject;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    protected void runTransformAssertions(String testPackId, String pipelineId, String dataSetName, TransformTestResult result) {
        actualExpectation.put(new TestPackAndDataSetName(testPackId, pipelineId, dataSetName), result);

        ExpectedAndActual<String> outputXml = result.getReport();
        assertEquals(outputXml.getExpected(), outputXml.getActual());

        ExpectedAndActual<String> keyValue = result.getKeyValue();
        TestingExpectationUtil.assertJsonEquals(keyValue.getExpected(), keyValue.getActual());

        ExpectedAndActual<Integer> validationFailures = result.getModelValidationFailures();
        assertEquals(validationFailures.getExpected(), validationFailures.getActual(), "Validation failures");

        ExpectedAndActual<Boolean> error = result.getRuntimeError();
        assertEquals(error.getExpected(), error.getActual(), "Error");
    }
}
