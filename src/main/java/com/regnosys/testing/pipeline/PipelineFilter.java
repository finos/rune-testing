package com.regnosys.testing.pipeline;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class PipelineFilter implements Predicate<String> {

    private final List<String> items;
    private final BiPredicate<String, String> predicate;

    private PipelineFilter(List<String> items, BiPredicate<String, String> predicate) {
        this.items = items;
        this.predicate = predicate;
    }

    public boolean test(String item) {
        return items.stream().anyMatch(x -> predicate.test(item, x));
    }

    public static Predicate<String> startsWith(String... startsFilter) {
        return new PipelineFilter(Arrays.asList(startsFilter), String::startsWith);
    }

    public static Predicate<String> contains(String... startsFilter) {
        return new PipelineFilter(Arrays.asList(startsFilter), String::contains);
    }

    public static Predicate<String> equalsTo(String... startsFilter) {
        return new PipelineFilter(Arrays.asList(startsFilter), String::equals);
    }

    public static Predicate<String> startsWith(List<String> startsFilterDefaults, String... startsFilter) {
        Collections.addAll(startsFilterDefaults, startsFilter);
        return new PipelineFilter(startsFilterDefaults, String::startsWith);
    }
}
