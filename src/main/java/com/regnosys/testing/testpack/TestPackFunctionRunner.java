package com.regnosys.testing.testpack;

import com.regnosys.rosetta.common.util.Pair;

import java.nio.file.Path;

import static com.regnosys.rosetta.common.transform.TestPackModel.SampleModel.Assertions;

interface TestPackFunctionRunner {
    Pair<String, Assertions> run(Path inputPath);
}
