package com.regnosys.testing.performance;

import com.google.common.base.Stopwatch;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class PerformanceTestHarness<I, O> {

    /**
     * Executes a performance test with the specified number of threads and runs.
     *
     * @param threads          The number of threads to use for concurrent execution.
     * @param runs            The number of test runs to perform.
     * @param performanceTest The performance test implementation.
     * @param <I>             The input data type.
     * @param <O>             The output data type.
     */
    public static <I, O> void execute(int threads, int runs, PerformanceTest<I, O> performanceTest) {
        new PerformanceTestHarness<I, O>(threads, runs).execute(performanceTest);
    }

    private final int threads;
    private final int runs;

    /**
     * Creates a new PerformanceTestHarness.
     *
     * @param threads The number of threads to use for concurrent execution.
     * @param runs    The number of test runs to perform.
     */
    public PerformanceTestHarness(int threads, int runs) {
        this.threads = threads;
        this.runs = runs;
    }

    /**
     * Executes the performance test.
     *
     * @param test The performance test implementation.
     */
    void execute(PerformanceTest<I, O> test) {
        // Wrap the test with unchecked exception handling
        UncheckedPerformanceTest<I, O> performanceTest = new UncheckedPerformanceTest<>(test);

        // Initialize the test state
        performanceTest.initState();

        // Load the test data
        List<I> testData = performanceTest.loadData();

        // Warm up the test by running it once
        performanceTest.run(testData.get(0));

        // Print test parameters
        System.out.printf("Timing test using %s files and %s concurrent API calls%n", testData.size(), threads);

        // Print header for the results table
        System.out.print("Run #\t");
        System.out.printf("%s concurrent API calls (%s files)\t", threads, testData.size());
        System.out.printf("average run (1 file)%n");

        // Run the test multiple times and collect timing data
        double averageTime = IntStream.range(1, runs + 1)
                .peek(i -> System.out.printf("%s\t", i)) // Print run number
                .mapToObj(x -> testRun(performanceTest, testData)) // Run the test
                .peek(i -> System.out.printf("%s\t", nanoToSeconds(i.toNanos()))) // Print total run time
                .map(i -> i.dividedBy(testData.size())) // Calculate average time per file
                .peek(i -> System.out.printf("%s%n", nanoToMilliseconds(i.toNanos()))) // Print average run time
                .mapToLong(Duration::getNano) // Convert to nanoseconds for averaging
                .average() // Calculate the average time
                .orElseThrow(() -> new RuntimeException("No Data")); // Throw exception if no data

        // Print overall average time
        System.out.printf("%nTook average time of %s for a single run using %s concurrent API calls%n", nanoToMilliseconds((long) averageTime), threads);
    }

    /**
     * Runs a single test iteration with concurrent execution.
     *
     * @param performanceTest The performance test implementation.
     * @param testData        The test data to use.
     * @return The elapsed time for the test run.
     */
    private Duration testRun(PerformanceTest<I, O> performanceTest, List<I> testData) {
        // Create an executor service with a fixed thread pool
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        // Create callables for each test data item
        List<Callable<O>> callables = testData.stream()
                .map(data -> callable(performanceTest, data))
                .collect(Collectors.toList());

        // Start the stopwatch and invoke all callables concurrently
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<Future<O>> futures = invoke(executorService, callables);

        // Collect the results from the futures
        List<O> results = futures.stream().map(this::dataFromFuture).collect(Collectors.toList());

        // Stop the stopwatch and get elapsed time
        Duration elapsed = stopwatch.elapsed();

        // Shut down the executor service
        executorService.shutdown();

        return elapsed;
    }

    /**
     * Creates a Callable for running the performance test with the given data.
     *
     * @param performanceTest The performance test implementation.
     * @param data            The input data for the test.
     * @return A Callable that runs the performance test.
     */
    private Callable<O> callable(PerformanceTest<I, O> performanceTest, I data) {
        return () -> performanceTest.run(data);
    }


    /**
     * Invokes all callables concurrently using the provided executor service.
     *
     * @param executorService The executor service to use.
     * @param callables       The list of callables to invoke.
     * @return A list of futures representing the results of the invocations.
     */
    private List<Future<O>> invoke(ExecutorService executorService, List<Callable<O>> callables) {
        try {
            return executorService.invokeAll(callables);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the result from a Future, handling potential exceptions.
     *
     * @param x The future to retrieve the result from.
     * @return The result of the future.
     */
    private O dataFromFuture(Future<O> x) {
        try {
            return x.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Formats a duration in nanoseconds to seconds with 2 decimal places.
     *
     * @param nanos The duration in nanoseconds.
     * @return The formatted duration string.
     */
    private String nanoToSeconds(long nanos) {
        double value = (double) nanos / NANOSECONDS.convert(1, TimeUnit.SECONDS);
        return String.format(Locale.ROOT, "%.2g", value) + "s";
    }

    /**
     * Formats a duration in nanoseconds to milliseconds with 3 decimal places.
     *
     * @param nanos The duration in nanoseconds.
     * @return The formatted duration string.
     */
    private String nanoToMilliseconds(long nanos) {
        double value = (double) nanos / NANOSECONDS.convert(1, TimeUnit.MILLISECONDS);
        return String.format(Locale.ROOT, "%.3g", value) + "ms";
    }

    /**
     * A wrapper for {@link PerformanceTest} that catches checked exceptions and rethrows them as unchecked exceptions.
     *
     * @param <I> The input data type.
     * @param <O> The output data type.
     */
    private static final class UncheckedPerformanceTest<I, O> implements PerformanceTest<I, O> {

        private final PerformanceTest<I, O> delegate;

        /**
         * Creates a new UncheckedPerformanceTest.
         *
         * @param delegate The performance test implementation to wrap.
         */
        public UncheckedPerformanceTest(PerformanceTest<I, O> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void initState() {
            try {
                delegate.initState();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<I> loadData() {
            try {
                return delegate.loadData();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public O run(I data) {
            try {
                return delegate.run(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}