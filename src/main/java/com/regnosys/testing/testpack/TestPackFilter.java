package com.regnosys.testing.testpack;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.rosetta.util.CollectionUtils.emptyIfNull;

public class TestPackFilter {

    public static TestPackFilter create() {
        return new TestPackFilter(null,
                Collections.emptyList(),
                ArrayListMultimap.create(),
                ArrayListMultimap.create());
    }

    public TestPackFilter withModelNamespaceRegex(String modelNamespaceRegex) {
        return new TestPackFilter(modelNamespaceRegex,
                this.excluded,
                this.testPackReportMap,
                this.reportTestPackMap);
    }

    public TestPackFilter withExcluded(List<Class<?>> excluded) {
        return new TestPackFilter(this.modelNamespaceRegex,
                excluded,
                this.testPackReportMap,
                this.reportTestPackMap);
    }

    public TestPackFilter withTestPackReportMap(ImmutableMultimap<String, Class<?>> testPackReportMap) {
        return new TestPackFilter(this.modelNamespaceRegex,
                this.excluded,
                testPackReportMap,
                this.reportTestPackMap);
    }

    public TestPackFilter withReportTestPackMap(ImmutableMultimap<Class<?>, String> reportTestPackMap) {
        return new TestPackFilter(this.modelNamespaceRegex,
                this.excluded,
                this.testPackReportMap,
                reportTestPackMap);
    }

    /**
     * Regex to filter namespaces, e.g. "^drr\\..*" to filter report or projections in namespace "drr.*"
     */
    private final String modelNamespaceRegex;
    private final List<Class<?>> excluded;

    /**
     * For the test pack name (e.g. "CFTC Event Scenarios") only run the specified reports (e.g. CFTCPart43ReportFunction.class)
     */
    private final ImmutableMultimap<String, Class<?>> testPackReportMap;

    /**
     * For the report (e.g. MASSFAMAS_2013ReportFunction.class) only use the specified test pack name (e.g. "Rates")
     */
    private final ImmutableMultimap<Class<?>, String> reportTestPackMap;

    public TestPackFilter(String modelNamespaceRegex,
                          List<Class<?>> excluded,
                          Multimap<String, Class<?>> testPackReportMap,
                          Multimap<Class<?>, String> reportTestPackMap) {
        this.modelNamespaceRegex = modelNamespaceRegex;
        this.excluded = Collections.unmodifiableList(emptyIfNull(excluded));
        this.testPackReportMap = ImmutableMultimap.copyOf(testPackReportMap);
        this.reportTestPackMap = ImmutableMultimap.copyOf(reportTestPackMap);
    }

    public String getModelNamespaceRegex() {
        return modelNamespaceRegex;
    }

    public Collection<Class<?>> getExcluded() {
        return excluded;
    }

    public ImmutableMultimap<String, Class<?>> getTestPackReportMap() {
        return testPackReportMap;
    }

    public ImmutableMultimap<Class<?>, String> getReportTestPackMap() {
        return reportTestPackMap;
    }
}