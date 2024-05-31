package com.regnosys.testing.testpack;

/*-
 * ==============
 * Rosetta Testing
 * ==============
 * Copyright (C) 2022 - 2024 REGnosys
 * ==============
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
 * ==============
 */

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

    List<RosettaModel> loadRosettaModels(ImmutableList<String> rosettaPaths, ClassLoader classLoader);

    List<RosettaReport> getReports(List<RosettaModel> models, String namespaceRegex, Collection<Class<?>> excludedReports);

    List<Function> getFunctionsWithAnnotation(List<RosettaModel> models, String namespaceRegex, String annotation, Collection<Class<?>> excluded);

    RosettaType getInputType(Function func);

    RosettaReport getUpstreamReport(List<RosettaModel> models, Function func, Collection<Class<?>> excluded);

    String toJavaClass(Function function);

    String toJavaClass(RosettaReport report);

    String toJavaClass(RosettaType rosettaType);
}
