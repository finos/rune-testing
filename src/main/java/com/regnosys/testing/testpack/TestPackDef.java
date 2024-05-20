package com.regnosys.testing.testpack;

import java.util.List;
import java.util.Set;

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
