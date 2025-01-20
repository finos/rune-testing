package com.regnosys.testing.performance;

/*-
 * ===============
 * Rune Testing
 * ===============
 * Copyright (C) 2022 - 2025 REGnosys
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


import java.util.List;

/**
 * Interface defining the contract for a performance test.
 * @param <I> The type of the input data.
 * @param <O> The type of the output data.
 */
public interface PerformanceTest<I, O> {

    /**
     * Initializes the state required for the performance test.
     * This method is called once before loading the data and running the test.
     * @throws Exception If any error occurs during initialization.
     */
    void initState() throws Exception;

    /**
     * Loads the data to be used for the performance test.
     * @return A list of input data objects.
     * @throws Exception If any error occurs during data loading.
     */
    List<I> loadData() throws Exception;

    /**
     * Runs a single iteration of the performance test with the given input data.
     * @param data The input data for this test run.
     * @return The output of the test run.
     * @throws Exception If any error occurs during the test run.
     */
    O run(I data) throws Exception;

}
