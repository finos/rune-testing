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
