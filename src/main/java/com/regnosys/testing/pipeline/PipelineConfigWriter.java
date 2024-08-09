package com.regnosys.testing.pipeline;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;

import static com.regnosys.testing.TestingExpectationUtil.TEST_WRITE_BASE_PATH;

public class PipelineConfigWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineConfigWriter.class);

    @Inject
    private PipelineModelWriter pipelineModelWriter;

    @Inject
    private PipelineTestPackWriter pipelineTestPackWriter;

    public void writePipelinesAndTestPacks(PipelineTreeConfig config) throws IOException {

        if (TEST_WRITE_BASE_PATH.isEmpty()) {
            LOGGER.error("TEST_WRITE_BASE_PATH not set");
            return;
        }
        Path writePath = TEST_WRITE_BASE_PATH.get();

        if(config.getWritePath() == null) {
            config.withWritePath(writePath);
        }

        pipelineModelWriter.writePipelines(config);
        pipelineTestPackWriter.writeTestPacks(config);
    }
}
