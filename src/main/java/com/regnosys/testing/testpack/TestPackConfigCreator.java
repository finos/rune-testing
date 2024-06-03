package com.regnosys.testing.testpack;

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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.ImplementedBy;
import com.google.inject.Injector;

import java.util.List;

@ImplementedBy(TestPackConfigCreatorImpl.class)
public interface TestPackConfigCreator {

    /**
     * Generates pipeline and test-pack config files.
     *
     * @param rosettaPaths    - list of folders that contain rosetta model files, e.g. "drr/rosetta"
     * @param filter          - provides filters to include or exclude
     * @param testPackDefs    - provides list of test-pack information such as test pack name, input type and sample input paths
     * @param outputSchemaMap - output Document type / xsd look up map
     * @param injector        - model runtime guice injector
     */
    void createPipelineAndTestPackConfig(ImmutableList<String> rosettaPaths,
                                         TestPackFilter filter,
                                         List<TestPackDef> testPackDefs,
                                         ImmutableMap<Class<?>, String> outputSchemaMap,
                                         Injector injector);
}
