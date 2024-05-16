package com.regnosys.testing.transform;

import com.regnosys.rosetta.common.transform.TestPackModel;

public class TransformTestResult {

    private final String output;
    private final TestPackModel.SampleModel sampleModel;

    public TransformTestResult(String output, TestPackModel.SampleModel sampleModel) {
        this.output = output;
        this.sampleModel = sampleModel;
    }

    public String getOutput() {
        return output;
    }

    public TestPackModel.SampleModel getSampleModel() {
        return sampleModel;
    }
}
