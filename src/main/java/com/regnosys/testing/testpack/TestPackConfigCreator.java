package com.regnosys.testing.testpack;

import com.google.common.collect.ImmutableList;
import com.google.inject.ImplementedBy;

@ImplementedBy(TestPackConfigCreatorImpl.class)
public interface TestPackConfigCreator {

    void createPipelineAndTestPackConfig(ImmutableList<String> rosettaPaths, TestPackFilter filter, TestPackDef testPackDef);
}
