package com.regnosys.testing.testpack;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.rosetta.util.CollectionUtils.emptyIfNull;

public class TestPackFilter {
    /**
     * Regex to filter namespaces, e.g. "^drr\\..*" to filter report or projections in namespace "drr.*"
     */
    private final String modelNamespaceRegex;
    private final Collection<Class<?>> excluded;

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
        this.excluded = Collections.unmodifiableCollection(emptyIfNull(excluded));
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