package com.regnosys.testing.pipeline;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.util.Pair;
import com.regnosys.testing.testpack.TestPackFunctionRunner;
import com.regnosys.testing.testpack.TestPackFunctionRunnerProvider;

import javax.inject.Inject;
import java.nio.file.Path;

public class PipelineFunctionRunner {

    @Inject
    Injector injector;

    @Inject
    TestPackFunctionRunnerProvider provider;

    public Result run(PipelineModel pipelineModel, ImmutableMap<Class<?>, String> outputSchemaMap, Path inputPath) {
        TestPackFunctionRunner functionRunner = getFunctionRunner(pipelineModel, outputSchemaMap);
        Pair<String, TestPackModel.SampleModel.Assertions> run = functionRunner.run(inputPath.toAbsolutePath());
        return new Result(run.left(), run.right());
    }

    private TestPackFunctionRunner getFunctionRunner(PipelineModel pipelineModel, ImmutableMap<Class<?>, String> outputSchemaMap) {
        if (pipelineModel.getOutputSerialisation() != null) {
            return provider.create(pipelineModel.getTransform(), pipelineModel.getOutputSerialisation(), outputSchemaMap, injector);
        } else {
            return provider.create(pipelineModel.getTransform(), injector);
        }
    }

    public static class Result {
        String serialisedOutput;
        TestPackModel.SampleModel.Assertions assertions;

        public Result(String serialisedOutput, TestPackModel.SampleModel.Assertions assertions) {
            this.serialisedOutput = serialisedOutput;
            this.assertions = assertions;
        }

        public TestPackModel.SampleModel.Assertions getAssertions() {
            return assertions;
        }

        public String getSerialisedOutput() {
            return serialisedOutput;
        }
    }

}
