package com.regnosys.testing.testpack;

import com.google.common.collect.ImmutableList;
import com.google.inject.ImplementedBy;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaReport;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Function;

import java.util.Collection;
import java.util.List;

@ImplementedBy(TestPackModelHelperImpl.class)
public interface TestPackModelHelper {

    List<RosettaModel> loadRosettaModels(ImmutableList<String> rosettaPaths);

    List<RosettaReport> getReports(List<RosettaModel> models, String namespaceRegex, Collection<Class<?>> excludedReports);

    List<Function> getFunctionsWithAnnotation(List<RosettaModel> models, String namespaceRegex, String annotation);

    RosettaType getInputType(Function func);

    RosettaReport getUpstreamReport(List<RosettaModel> models, Function func, Collection<Class<?>> excluded);

    String toJavaClass(Function function);

    String toJavaClass(RosettaReport report);

    String toJavaClass(RosettaType rosettaType);
}
