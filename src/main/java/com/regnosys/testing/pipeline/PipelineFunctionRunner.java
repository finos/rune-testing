package com.regnosys.testing.pipeline;

/*-
 * ===============
 * Rosetta Testing
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

import java.nio.file.Path;

public interface PipelineFunctionRunner {

    /**
     * Pipeline function execution.
     *
     * @param inputPath path from repository root for input sample
     * @return serialised output, validation report and assertions
     */
    PipelineFunctionResult run(Path inputPath);
}
