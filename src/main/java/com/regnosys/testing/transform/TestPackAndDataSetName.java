package com.regnosys.testing.transform;

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
