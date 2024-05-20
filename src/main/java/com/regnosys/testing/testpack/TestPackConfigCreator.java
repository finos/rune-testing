package com.regnosys.testing.testpack;

import com.google.common.collect.ImmutableList;
import com.google.inject.ImplementedBy;

import java.util.List;

@ImplementedBy(TestPackConfigCreatorImpl.class)
public interface TestPackConfigCreator {

    void createPipelineAndTestPackConfig(ImmutableList<String> rosettaPaths, TestPackFilter filter, List<TestPackDef> testPackDefs);
}
