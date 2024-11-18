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


import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Deprecated
public class PipelineTestPackFilter {

    public static PipelineTestPackFilter create() {
        return new PipelineTestPackFilter(
                Collections.emptyList(),
                ArrayListMultimap.create(),
                ArrayListMultimap.create(),
                ArrayListMultimap.create());
    }

    public PipelineTestPackFilter withExcludedFunctionsFromTestPackGeneration(List<Class<?>> excludedFunctionsFromTestPackGeneration) {
        return new PipelineTestPackFilter(
                excludedFunctionsFromTestPackGeneration,
                this.testPacksRestrictedForFunctions,
                this.testPacksSpecificToFunctions,
                this.functionsSpecificToTestPacks);
    }

    /**
     * Defines the test packs that are restricted to specific functions and need to be executed only for those functions.
     * These test packs are added in addition to other test packs for the function.
     */
    public PipelineTestPackFilter withTestPacksRestrictedForFunctions(ImmutableMultimap<String, Class<?>> testPacksRestrictedForFunctions) {
        return new PipelineTestPackFilter(
                this.excludedFunctionsFromTestPackGeneration,
                testPacksRestrictedForFunctions,
                this.testPacksSpecificToFunctions,
                this.functionsSpecificToTestPacks);
    }

    /**
     * Defines the test packs that are specific to certain functions. Only these test packs will be run for the specified functions
     * and will not be included for others.
     */
    public PipelineTestPackFilter withTestPacksSpecificToFunctions(ImmutableMultimap<String, Class<?>> testPacksSpecificToFunctions) {
        return new PipelineTestPackFilter(
                this.excludedFunctionsFromTestPackGeneration,
                this.testPacksRestrictedForFunctions,
                testPacksSpecificToFunctions,
                this.functionsSpecificToTestPacks);
    }

    /**
     * Defines the functions that are specific to certain test packs. These functions will only run these test packs, but the packs
     * can still be included for other functions.
     */
    public PipelineTestPackFilter withFunctionsSpecificToTestPacks(ImmutableMultimap<Class<?>, String> functionsSpecificToTestPacks) {
        return new PipelineTestPackFilter(
                this.excludedFunctionsFromTestPackGeneration,
                this.testPacksRestrictedForFunctions,
                this.testPacksSpecificToFunctions,
                functionsSpecificToTestPacks);
    }

    private final List<Class<?>> excludedFunctionsFromTestPackGeneration;
    private final ImmutableMultimap<String, Class<?>> testPacksRestrictedForFunctions;
    private final ImmutableMultimap<String, Class<?>> testPacksSpecificToFunctions;
    private final ImmutableMultimap<Class<?>, String> functionsSpecificToTestPacks;

    public PipelineTestPackFilter(List<Class<?>> excludedFunctionsFromTestPackGeneration,
                                  Multimap<String, Class<?>> testPacksRestrictedForFunctions,
                                  Multimap<String, Class<?>> testPacksSpecificToFunctions,
                                  Multimap<Class<?>, String> functionsSpecificToTestPacks) {
        this.excludedFunctionsFromTestPackGeneration = excludedFunctionsFromTestPackGeneration;
        this.testPacksRestrictedForFunctions = ImmutableMultimap.copyOf(testPacksRestrictedForFunctions);
        this.testPacksSpecificToFunctions = ImmutableMultimap.copyOf(testPacksSpecificToFunctions);
        this.functionsSpecificToTestPacks = ImmutableMultimap.copyOf(functionsSpecificToTestPacks);
    }

    public Collection<Class<?>> getExcludedFunctionsFromTestPackGeneration() {
        return excludedFunctionsFromTestPackGeneration;
    }

    public ImmutableMultimap<String, Class<?>> getTestPacksRestrictedForFunctions() {
        return testPacksRestrictedForFunctions;
    }

    public ImmutableMultimap<Class<?>, String> getFunctionsSpecificToTestPacks() {
        return functionsSpecificToTestPacks;
    }

    public ImmutableMultimap<String, Class<?>> getTestPacksSpecificToFunctions() {
        return testPacksSpecificToFunctions;
    }
}
