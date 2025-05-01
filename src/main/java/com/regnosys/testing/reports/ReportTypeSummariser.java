package com.regnosys.testing.reports;

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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.inject.Injector;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.generator.java.types.JavaTypeTranslator;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaReport;
import com.regnosys.rosetta.transgest.ModelLoader;
import com.regnosys.rosetta.types.*;
import com.regnosys.rosetta.utils.ModelIdProvider;
import com.rosetta.model.lib.ModelReportId;
import com.rosetta.model.lib.functions.LabelProvider;
import com.rosetta.model.lib.path.RosettaPath;
import com.rosetta.util.DottedPath;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Util to help create comparison of types/attributes between model versions, for example DRR 5.x.x and 6-dev.
 */
public class ReportTypeSummariser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportTypeSummariser.class);

    private final ModelLoader modelLoader;
    private final Injector injector;
    private final RObjectFactory rObjectFactory;
    private final JavaTypeTranslator javaTypeTranslator;
    private final ModelIdProvider modelIdProvider;

    @Inject
    public ReportTypeSummariser(ModelLoader modelLoader, Injector injector, RObjectFactory rObjectFactory, JavaTypeTranslator javaTypeTranslator) {
        this.modelLoader = modelLoader;
        this.injector = injector;
        this.rObjectFactory = rObjectFactory;
        this.javaTypeTranslator = javaTypeTranslator;
        this.modelIdProvider = new ModelIdProvider();
    }

    /**
     * Finds all reports in the model with the given namespace and filter, and creates text files containing a list of type (and related type) attributes, including the label, path and attribute type.
     *
     * @param classpathDir          path to model files, e.g. rosetta/drr
     * @param namespace             namespace starts with, e.g. drr
     * @param excludedReportsFilter reports to be excluded if the name contains any items
     * @param version               version name, used in the output file name
     * @param basePath              write path, typically a temp directory
     */
    public void createTypeSummaryForReports(String classpathDir, DottedPath namespace, Set<String> excludedReportsFilter, String version, Path basePath) {
        List<RosettaModel> models = getModels(classpathDir);

        findReports(models, namespace, excludedReportsFilter)
                .forEach(report -> {
                    ModelReportId reportId = modelIdProvider.getReportId(report);
                    LOGGER.info("Creating report type summary for {}", reportId);
                    RDataType rReportType = rObjectFactory.buildRDataType(report.getReportType());
                    RFunction rFunction = rObjectFactory.buildRFunction(report);
                    LabelProvider labelProvider = getLabelProvider(rFunction);
                    Multimap<RType, Data> collectedTypeAttributes = ArrayListMultimap.create();
                    collectTypeAttributes(rReportType, null, labelProvider, collectedTypeAttributes);
                    String content = writeReportTypeSummary(collectedTypeAttributes);
                    writeFile(basePath.resolve(getFileName(report, version)), content);
                });
    }

    /**
     * Merges 2 report type summary files into a single file that can be compared in Excel.
     *
     * @param classpathDir          path to model files, e.g. rosetta/drr
     * @param namespace             namespace starts with, e.g. drr
     * @param excludedReportsFilter reports to be excluded if the name contains any items
     * @param branch1               first branch to merge
     * @param branch2               second branch to merge
     * @param basePath              write path, typically a temp directory
     */
    public void mergeTypeSummaryForReports(String classpathDir, DottedPath namespace, Set<String> excludedReportsFilter, String branch1, String branch2, Path basePath) {
        List<RosettaModel> models = getModels(classpathDir);

        for (RosettaReport report : findReports(models, namespace, excludedReportsFilter)) {
            ModelReportId reportId = modelIdProvider.getReportId(report);
            LOGGER.info("Merging report type data for {}", reportId);
            Path file1 = basePath.resolve(getFileName(report, branch1));
            if (!Files.exists(file1)) {
                LOGGER.info("{} not found for {} version {}", file1, reportId, branch1);
                continue;
            }
            Path file2 = basePath.resolve(getFileName(report, branch2));
            if (!Files.exists(file2)) {
                LOGGER.info("{} not found for {} version {}", file2, reportId, branch2);
                continue;
            }

            Multimap<String, Data> content1 = getLabelToDataMap(file1);
            Multimap<String, Data> content2 = getLabelToDataMap(file2);

            Set<String> keys = Stream.of(content1.keySet(), content2.keySet())
                    .flatMap(Collection::stream)
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s||||%s||\n", branch1, branch2));
            sb.append("Label|Path|Type||Label|Path|Type\n");
            for (String key : keys) {
                Iterator<Data> data1 = content1.get(key).iterator();
                Iterator<Data> data2 = content2.get(key).iterator();
                while (data1.hasNext() || data2.hasNext()) {
                    sb.append(data1.hasNext() ? data1.next().toExcelFormat() : "||");
                    sb.append("||");
                    sb.append(data2.hasNext() ? data2.next().toExcelFormat() : "||");
                    sb.append("\n");
                }

            }
            writeFile(basePath.resolve(getFileName(report, "merged")), sb.toString());
        }
    }

    /**
     * Merges 2 report type summary files into a single file that can be compared in Excel.
     *
     * @param classpathDir path to model files, e.g. rosetta/drr
     * @param reportId1    first report to merge
     * @param branch1      first branch to merge
     * @param reportId2    second report to merge
     * @param branch2      second branch to merge
     * @param basePath     write path, typically a temp directory
     */
    public void mergeTypeSummaryForReport(String classpathDir, ModelReportId reportId1, String branch1, ModelReportId reportId2, String branch2, Path basePath) {
        List<RosettaModel> models = getModels(classpathDir);

        RosettaReport report1 = findReport(models, reportId1);
        LOGGER.info("Merging report type data for {}", reportId1);
        Path file1 = basePath.resolve(getFileName(report1, branch1));
        if (!Files.exists(file1)) {
            LOGGER.info("{} not found for {} version {}", file1, reportId1, branch1);
            return;
        }
        RosettaReport report2 = findReport(models, reportId2);
        Path file2 = basePath.resolve(getFileName(report2, branch2));
        if (!Files.exists(file2)) {
            LOGGER.info("{} not found for {} version {}", file2, report2, branch2);
            return;
        }

        Multimap<String, Data> content1 = getLabelToDataMap(file1);
        Multimap<String, Data> content2 = getLabelToDataMap(file2);

        Set<String> keys = Stream.of(content1.keySet(), content2.keySet())
                .flatMap(Collection::stream)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s||||%s||\n", file1.getFileName(), file2.getFileName()));
        sb.append("Label|Path|Type||Label|Path|Type\n");
        for (String key : keys) {
            Iterator<Data> data1 = content1.get(key).iterator();
            Iterator<Data> data2 = content2.get(key).iterator();
            while (data1.hasNext() || data2.hasNext()) {
                sb.append(data1.hasNext() ? data1.next().toExcelFormat() : "||");
                sb.append("||");
                sb.append(data2.hasNext() ? data2.next().toExcelFormat() : "||");
                sb.append("\n");
            }

        }
        writeFile(basePath.resolve(getFileName(report2, "merged")), sb.toString());

    }

    @NotNull
    private String getFileName(RosettaReport report, String version) {
        ModelReportId reportId = modelIdProvider.getReportId(report);
        return String.format("%s-%s.csv", reportId.joinRegulatoryReference("-"), version);
    }

    private String writeReportTypeSummary(Multimap<RType, Data> typeAttributeMap) {
        StringBuilder sb = new StringBuilder();
        typeAttributeMap.values().stream()
                .filter(x -> x.label != null)
                .map(Data::toExcelFormat)
                .sorted()
                .forEach(s -> sb.append(s).append("\n"));
        return sb.toString();
    }

    private List<RosettaModel> getModels(String classpathDir) {
        List<URL> urls = ClassPathUtils
                .findPathsFromClassPath(ImmutableList.of("model", classpathDir),
                        ".*\\.rosetta",
                        Optional.empty(),
                        this.getClass().getClassLoader())
                .stream()
                .map(UrlUtils::toUrl)
                .collect(Collectors.toList());
        List<RosettaModel> models = modelLoader.loadRosettaModels(urls.stream());
        if (models.size() <= 2) {
            throw new IllegalArgumentException("No model rosetta files found.  Only found basic types and annotations rosetta files.");
        }
        return models;
    }

    private LabelProvider getLabelProvider(RFunction rFunction) {
        try {
            Class<? extends LabelProvider> labelProviderClass = javaTypeTranslator.toLabelProviderJavaClass(rFunction).loadClass(getClass().getClassLoader());
            return injector.getInstance(labelProviderClass);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<RosettaReport> findReports(List<RosettaModel> models, DottedPath namespace, Set<String> excludedReportsFilter) {
        return models.stream()
                .map(RosettaModel::getElements)
                .flatMap(Collection::stream)
                .filter(RosettaReport.class::isInstance)
                .map(RosettaReport.class::cast)
                .filter(r -> modelIdProvider.toDottedPath(r.getModel()).startsWith(namespace))
                .filter(r ->
                        Arrays.stream(modelIdProvider.getReportId(r).getCorpusList())
                                .noneMatch(excludedReportsFilter::contains))
                .collect(Collectors.toSet());
    }

    private RosettaReport findReport(List<RosettaModel> models, ModelReportId reportId) {
        return models.stream()
                .map(RosettaModel::getElements)
                .flatMap(Collection::stream)
                .filter(RosettaReport.class::isInstance)
                .map(RosettaReport.class::cast)
                .filter(r ->
                        modelIdProvider.getReportId(r).equals(reportId))
                .findFirst().orElseThrow();
    }

    private void collectTypeAttributes(RDataType parentDataType, RosettaPath path, LabelProvider labelProvider, Multimap<RType, Data> visitor) {
        parentDataType.getAllAttributes()
                .forEach(attribute -> {
                    // String name = attribute.getName();
                    RosettaPath.Element pathElement = getPathElement(attribute);
                    RosettaPath attributePath = Optional.ofNullable(path)
                            .map(p -> p.newSubPath(pathElement))
                            .orElse(RosettaPath.createPath(pathElement));
                    RType attributeType = attribute.getRMetaAnnotatedType().getRType();
                    if (attributeType instanceof RDataType) {
                        RDataType childDataType = (RDataType) attributeType;
                        // collect attributes for child type
                        collectTypeAttributes(childDataType, attributePath, labelProvider, visitor);
                    } else {
                        // add reported attribute if it has a label
                        String label = labelProvider.getLabel(attributePath);
                        if (label != null) {
                            visitor.put(parentDataType, Data.of(label, attributePath, attribute));
                        }
                    }
                });
    }

    private RosettaPath.Element getPathElement(RAttribute attribute) {
        String name = attribute.getName();
        return RosettaPath.Element.create(name, attribute.isMulti() ? OptionalInt.of(0) : OptionalInt.empty(), Map.of());
    }

    private void writeFile(Path writePath, String content) {
        try {
            Files.createDirectories(writePath.getParent());
            Files.write(writePath, content.getBytes());
            LOGGER.info("Wrote output to {}", writePath);
        } catch (IOException e) {
            LOGGER.error("Failed to write output to {}", writePath, e);
        }
    }

    @NotNull
    private Multimap<String, Data> getLabelToDataMap(Path path) {
        Multimap<String, Data> labelToDataMap = ArrayListMultimap.create();
        try {
            Files.readAllLines(path).stream()
                    .map(Data::parseExcelFormat)
                    .forEach(d -> labelToDataMap.put(d.getLabel(), d));
            return labelToDataMap;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read line from path " + path, e);
        }
    }

    private static class Data {
        private final String label;
        private final String path;
        private final String type;

        private Data(String label, String path, String type) {
            this.label = label;
            this.path = path;
            this.type = type;
        }

        static Data of(String label, RosettaPath path, RAttribute rAttribute) {
            return new Data(
                    label,
                    path.buildPath().replace("(0)", "[]"),
                    rAttribute.getRMetaAnnotatedType().getRType().getName());
        }

        static Data parseExcelFormat(String excelRow) {
            String[] parts = excelRow.split("\\|");
            return new Data(parts[0], parts[1], parts[2]);
        }

        public String getLabel() {
            return label;
        }

        String toExcelFormat() {
            return String.format("%s|%s|%s", label, path, type);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Data data = (Data) o;
            return Objects.equals(label, data.label) && Objects.equals(path, data.path) && Objects.equals(type, data.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label, path, type);
        }

        @Override
        public String toString() {
            return "Data{" +
                    "label='" + label + '\'' +
                    ", path='" + path + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
    }
}
