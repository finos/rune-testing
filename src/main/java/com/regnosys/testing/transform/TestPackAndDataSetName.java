package com.regnosys.testing.transform;

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

import java.util.Objects;

@Deprecated
public class TestPackAndDataSetName {
    public final String testPackID;
    public final String pipeLineId;
    public final String dataSetName;


    public TestPackAndDataSetName(String testPackID, String pipeLineId, String dataSetName) {
        this.testPackID = testPackID;
        this.pipeLineId = pipeLineId;
        this.dataSetName = dataSetName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestPackAndDataSetName)) return false;
        TestPackAndDataSetName that = (TestPackAndDataSetName) o;
        return Objects.equals(testPackID, that.testPackID) && Objects.equals(pipeLineId, that.pipeLineId) && Objects.equals(dataSetName, that.dataSetName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testPackID, pipeLineId, dataSetName);
    }

    public String getTestPackID() {
        return testPackID;
    }

    public String getPipeLineId() {
        return pipeLineId;
    }

    public String getDataSetName() {
        return dataSetName;
    }
}
