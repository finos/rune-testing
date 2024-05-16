package com.regnosys.testing.testpack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.inject.ImplementedBy;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaReport;
import com.regnosys.rosetta.rosetta.simple.Function;

import java.util.List;

@ImplementedBy(TestPackModelHelperImpl.class)
public interface TestPackModelHelper {

    List<RosettaModel> loadRosettaModels(ImmutableList<String> rosettaFolderPathNames);

    // Reports

    List<RosettaReport> getReports(List<RosettaModel> models, String namespaceRegex);

    PipelineModel createReportPipelineModel(RosettaReport report);

    List<TestPackModel> createReportTestPacks(List<RosettaReport> reports, TestPackDef testPackDef, ImmutableMultimap<Class<?>, String> reportIncludedTestPack, ImmutableMultimap<String, Class<?>> testPackIncludedReports);


    // Projections

    List<Function> getProjectionFunctions(List<RosettaModel> models, String namespaceRegex);

    PipelineModel createProjectionPipelineModel(List<RosettaModel> models, Function func);

    List<TestPackModel> createProjectionTestPacks(List<PipelineModel> projectionPipelines, List<RosettaReport> reports, List<TestPackModel> reportTestPacks);


}
