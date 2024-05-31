package com.regnosys.testing.reports;

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

import java.nio.file.Path;

@Deprecated // is this used?
public class ExpectedAndActual<T> {

    private final Path expectationPath;
    private final T expected;
    private final T actual;

    public ExpectedAndActual(Path expectationPath, T expected, T actual) {
        this.expectationPath = expectationPath;
        this.expected = expected;
        this.actual = actual;
    }

    public Path getExpectationPath() {
        return expectationPath;
    }

    public T getExpected() {
        return expected;
    }

    public T getActual() {
        return actual;
    }
}
