package com.regnosys.testing.testpack;

/*-
 * #%L
 * Rosetta Testing
 * %%
 * Copyright (C) 2022 - 2024 REGnosys
 * %%
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
 * #L%
 */

import java.util.List;

/**
 * Basic test pack definition.
 */
public class TestPackDef {
    private final String name;
    private final String inputType;
    private final List<String> inputPaths;

    public TestPackDef(String name, String inputType, List<String> inputPaths) {
        this.name = name;
        this.inputType = inputType;
        this.inputPaths = inputPaths;
    }

    public String getName() {
        return name;
    }

    public String getInputType() {
        return inputType;
    }

    public List<String> getInputPaths() {
        return inputPaths;
    }
}
