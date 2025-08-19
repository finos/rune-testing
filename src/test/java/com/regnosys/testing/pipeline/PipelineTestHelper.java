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
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.testing.CompiledCode;
import com.regnosys.testing.ModelHelper;
import com.regnosys.testing.RosettaTestingInjectorProvider;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.functions.RosettaFunction;
import com.rosetta.model.lib.qualify.QualifyFunctionFactory;
import com.rosetta.model.lib.validation.ValidatorFactory;

import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PipelineTestHelper {

    static final String SomeTypeClass = "pipeline.chain.test.SomeType";
    static final String StartClass = "pipeline.chain.test.functions.Start";
    static final String MiddleClass = "pipeline.chain.test.functions.Middle";
    static final String MiddleAClass = "pipeline.chain.test.functions.MiddleA";
    static final String MiddleBClass = "pipeline.chain.test.functions.MiddleB";
    static final String MiddleCClass = "pipeline.chain.test.functions.MiddleC";
    static final String MiddleDClass = "pipeline.chain.test.functions.MiddleD";
    static final String MiddleEClass = "pipeline.chain.test.functions.MiddleE";
    static final String EndClass = "pipeline.chain.test.functions.End";
    static final String EndAClass = "pipeline.chain.test.functions.EndA";
    static final String EndBClass = "pipeline.chain.test.functions.EndB";


    @Inject
    private ModelHelper modelHelper;

    private Path testPath;
    private CompiledCode compiledCode;

    static void setupInjector(Object caller) {
        Injector injector = new RosettaTestingInjectorProvider().getInjector();
        Injector childInjector = injector.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ReferenceConfig.class).toInstance(ReferenceConfig.noScopeOrExcludedPaths());
                bind(ValidatorFactory.class).to(ValidatorFactory.Default.class);
                bind(QualifyFunctionFactory.class).to(QualifyFunctionFactory.Default.class);
            }
        });
        childInjector.injectMembers(caller);
    }

    @Inject
    void init() throws Exception {
        testPath = Path.of("src/test/resources/pipeline-test");
        compiledCode = compileCode();
    }

    Class<RosettaFunction> endBClass() {
        return compiledCode.loadClass(EndBClass);
    }

    Class<RosettaFunction> endAClass() {
        return compiledCode.loadClass(EndAClass);
    }

    Class<RosettaFunction> middleAClass() {
        return compiledCode.loadClass(MiddleAClass);
    }

    Class<RosettaFunction> endClass() {
        return compiledCode.loadClass(EndClass);
    }

    Class<RosettaModelObject> someTypeClass() {
        return compiledCode.loadClass(SomeTypeClass);
    }

    Class<RosettaFunction> startClass() {
        return compiledCode.loadClass(StartClass);
    }

    Class<RosettaFunction> middleClass() {return compiledCode.loadClass(MiddleClass); }

    Class<RosettaFunction> middleBClass() {return compiledCode.loadClass(MiddleBClass); }

    Class<RosettaFunction> middleCClass() {return compiledCode.loadClass(MiddleCClass); }

    Class<RosettaFunction> middleDClass() {return compiledCode.loadClass(MiddleDClass); }

    Class<RosettaFunction> middleEClass() {return compiledCode.loadClass(MiddleEClass); }


    PipelineTreeConfig createNestedTreeConfig() {
        return new PipelineTreeConfig("testPrefix")
                .starting(TransformType.ENRICH, startClass())
                .add(startClass(), TransformType.REPORT, middleAClass())
                .add(startClass(), TransformType.REPORT, middleBClass())
                .add(middleAClass(), TransformType.PROJECTION, endAClass())
                .add(middleAClass(), TransformType.PROJECTION, endBClass())
                .add(middleBClass(), TransformType.PROJECTION, endAClass())
                .add(middleBClass(), TransformType.PROJECTION, endBClass());
    }

    PipelineTreeConfig createCsvConfig(Path csvTestPackSourceFile) {
        ImmutableMap<Class<?>, PipelineModel.Serialisation.Format> inputSerialisationFormat = ImmutableMap.<Class<?>, PipelineModel.Serialisation.Format>builder()
                .put(someTypeClass(), PipelineModel.Serialisation.Format.CSV)
                .build();

        return new PipelineTreeConfig("testPrefix")
                .starting(TransformType.TRANSLATE, startClass())
                .withInputSerialisationFormatMap(inputSerialisationFormat)
                .withCsvTestPackSourceFiles(List.of(csvTestPackSourceFile));
    }

    PipelineTreeConfig createTreeConfig() {
        return new PipelineTreeConfig("testPrefix")
                .starting(TransformType.ENRICH, startClass())
                .add(startClass(), TransformType.REPORT, middleClass())
                .add(middleClass(), TransformType.PROJECTION, endClass());
    }

    PipelineTreeConfig createNestedTreeConfigMultipleStartingNodes() {
        return new PipelineTreeConfig("testPrefix")
                .starting(TransformType.REPORT, middleAClass())
                .add(middleAClass(), TransformType.PROJECTION, endAClass())
                .add(middleAClass(), TransformType.PROJECTION, endBClass())

                .starting(TransformType.REPORT, middleBClass())
                .add(middleBClass(), TransformType.PROJECTION, endAClass())
                .add(middleBClass(), TransformType.PROJECTION, endBClass());
    }

    PipelineTreeConfig createTreeConfigWithoutStarting() {
        return new PipelineTreeConfig("testPrefix")
                .add(startClass(), TransformType.REPORT, middleClass())
                .add(middleClass(), TransformType.PROJECTION, endClass());
    }

    private CompiledCode compileCode() throws Exception {
        CompiledCode compiledCode = modelHelper.generateAndCompileJava(Files.readString(testPath.resolve("rosetta/pipelines.rosetta")));

        Thread.currentThread().setContextClassLoader(new ClassLoader(this.getClass().getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (compiledCode.classNames().contains(name)) {
                    return compiledCode.loadClass(name);
                }
                return super.loadClass(name);
            }
        });
        return compiledCode;
    }

}
