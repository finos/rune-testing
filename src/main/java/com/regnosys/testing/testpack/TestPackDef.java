package com.regnosys.testing.testpack;

import java.util.List;
import java.util.Set;

public interface TestPackDef {
    Set<NameAndInputType> getTestPacks();

    List<String> getTestPackInputPaths(String testPackName);
}
