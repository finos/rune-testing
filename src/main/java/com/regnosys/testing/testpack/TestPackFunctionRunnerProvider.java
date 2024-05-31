package com.regnosys.testing.testpack;

import com.google.common.collect.ImmutableMap;
import com.google.inject.ImplementedBy;
import com.google.inject.Injector;

import static com.regnosys.rosetta.common.transform.PipelineModel.Serialisation;
import static com.regnosys.rosetta.common.transform.PipelineModel.Transform;

@ImplementedBy(TestPackFunctionRunnerProviderImpl.class)
public interface TestPackFunctionRunnerProvider {
    
    TestPackFunctionRunner create(Transform transform, Injector injector);

    TestPackFunctionRunner create(Transform transform, Serialisation outputSerialisation, ImmutableMap<Class<?>, String> outputSchemaMap, Injector injector);
}
