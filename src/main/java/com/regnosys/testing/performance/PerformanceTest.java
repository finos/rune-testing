package com.regnosys.testing.performance;


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