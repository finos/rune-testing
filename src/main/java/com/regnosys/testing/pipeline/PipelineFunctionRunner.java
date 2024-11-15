package com.regnosys.testing.pipeline;

/*-
 * ===============
 * Rune Testing
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
        Pair<String, TestPackModel.SampleModel.Assertions> result = functionRunner.run(inputPath);
        return new Result(result.left(), result.right());
    }

    private TestPackFunctionRunner getFunctionRunner(PipelineModel pipelineModel, ImmutableMap<Class<?>, String> schemaMap) {
        return provider.create(pipelineModel.getTransform(), pipelineModel.getInputSerialisation(), pipelineModel.getOutputSerialisation(), schemaMap, injector);
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
