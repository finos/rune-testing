package com.regnosys.testing.transform;

import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.testing.reports.ExpectedAndActual;

public class TransformTestResult {

    private final TestPackModel.SampleModel sampleModel;
    private final ExpectedAndActual<String> keyValue;
    private final ExpectedAndActual<String> report;
    private final ExpectedAndActual<Integer> modelValidationFailures;
    private final ExpectedAndActual<Boolean> schemaValidationFailure;
    private final ExpectedAndActual<Boolean> runtimeError;


    public TransformTestResult(TestPackModel.SampleModel sampleModel,
                               ExpectedAndActual<String> keyValue,
                               ExpectedAndActual<String> report,
                               ExpectedAndActual<Integer> modelValidationFailures,
                               ExpectedAndActual<Boolean> schemaValidationFailure,
                               ExpectedAndActual<Boolean> runtimeError) {
        this.sampleModel = sampleModel;
        this.keyValue = keyValue;
        this.report = report;
        this.modelValidationFailures = modelValidationFailures;
        this.schemaValidationFailure = schemaValidationFailure;
        this.runtimeError = runtimeError;

    }

    public ExpectedAndActual<String> getKeyValue() {
        return keyValue;
    }

    public ExpectedAndActual<String> getReport() {
        return report;
    }

    public TestPackModel.SampleModel getSampleModel() {
        return sampleModel;
    }

    public ExpectedAndActual<Integer> getModelValidationFailures() {
        return modelValidationFailures;
    }

    public ExpectedAndActual<Boolean> getSchemaValidationFailure() {
        return schemaValidationFailure;
    }

    public ExpectedAndActual<Boolean> getRuntimeError() {
        return runtimeError;
    }
}
