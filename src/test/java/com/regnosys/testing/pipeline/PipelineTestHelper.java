package com.regnosys.testing.pipeline;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.testing.CompiledCode;
import com.regnosys.testing.ModelHelper;
import com.regnosys.testing.RosettaTestingInjectorProvider;
import com.rosetta.model.lib.functions.RosettaFunction;
import com.rosetta.model.lib.validation.ValidatorFactory;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;

public class PipelineTestHelper {

    static final String StartClass = "pipeline.chain.test.functions.Start";
    static final String MiddleClass = "pipeline.chain.test.functions.Middle";
    static final String MiddleAClass = "pipeline.chain.test.functions.MiddleA";
    static final String MiddleBClass = "pipeline.chain.test.functions.MiddleB";
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
            }
        });
        childInjector.injectMembers(caller);
    }

    @Inject
    void init() throws Exception {
        testPath = Path.of("src/test/resources/pipeline-test");
        compiledCode = compileCode();
    }

    Path testPath() {
        return testPath;
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

    Class<RosettaFunction> startClass() {
        return compiledCode.loadClass(StartClass);
    }

    Class<RosettaFunction> middleClass() {
        return compiledCode.loadClass(MiddleClass);
    }

    Class<RosettaFunction> middleBClass() {
        return compiledCode.loadClass(MiddleBClass);
    }


    PipelineTreeConfig createNestedTreeConfig() {
        return new PipelineTreeConfig()
                .starting(TransformType.ENRICH, startClass())
                .add(startClass(), TransformType.REPORT, middleAClass())
                .add(startClass(), TransformType.REPORT, middleBClass())
                .add(middleAClass(), TransformType.PROJECTION, endAClass())
                .add(middleAClass(), TransformType.PROJECTION, endBClass())
                .add(middleBClass(), TransformType.PROJECTION, endAClass())
                .add(middleBClass(), TransformType.PROJECTION, endBClass());
    }


    PipelineTreeConfig createTreeConfig() {
        return new PipelineTreeConfig()
                .starting(TransformType.ENRICH, startClass())
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
