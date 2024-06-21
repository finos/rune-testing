package com.regnosys.testing.testpack;

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

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.inject.Injector;
import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TestPackUtils;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.util.Pair;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaNamed;
import com.regnosys.rosetta.rosetta.RosettaReport;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Function;
import com.regnosys.testing.reports.FileNameProcessor;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import com.rosetta.model.lib.ModelReportId;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.util.DottedPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.regnosys.rosetta.common.transform.TestPackModel.SampleModel;
import static com.regnosys.rosetta.common.transform.TestPackModel.SampleModel.Assertions;
import static com.regnosys.rosetta.common.transform.TestPackUtils.*;
import static com.regnosys.testing.TestingExpectationUtil.TEST_WRITE_BASE_PATH;
import static com.regnosys.testing.TestingExpectationUtil.writeFile;
import static com.regnosys.testing.projection.ProjectionPaths.getProjectionDataItemOutputPath;

public class TestPackConfigCreatorImpl implements TestPackConfigCreator {
    private final Logger LOGGER = LoggerFactory.getLogger(TestPackConfigCreatorImpl.class);

    @Inject
    TestPackModelHelper modelHelper;
    @Inject
    TestPackFunctionRunnerProvider functionRunnerProvider;

    /**
     * Generates pipeline and test-pack config files.
     *
     * @param rosettaPaths      - list of folders that contain rosetta model files, e.g. "drr/rosetta"
     * @param filter            - provides filters to include or exclude
     * @param testPackDefs      - provides list of test-pack information such as test pack name, input type and sample input paths
     * @param outputSchemaMap   - output Document type / xsd look up map
     * @param injector          - model runtime guice injector
     */
    @Override
    public void createPipelineAndTestPackConfig(ImmutableList<String> rosettaPaths,
                                                TestPackFilter filter,
                                                List<TestPackDef> testPackDefs,
                                                ImmutableMap<Class<?>, String> outputSchemaMap,
                                                Injector injector) {
        if (TEST_WRITE_BASE_PATH.isEmpty()) {
            LOGGER.error("TEST_WRITE_BASE_PATH not set");
            return;
        }

        Path writePath = TEST_WRITE_BASE_PATH.get();
        TestPackConfigWriter testPackConfigWriter = new TestPackConfigWriter(ObjectMapperGenerator.createWriterMapper());

        LOGGER.info("Loading models");
        List<RosettaModel> rosettaModels = modelHelper.loadRosettaModels(rosettaPaths, this.getClass().getClassLoader());

        LOGGER.info("Report pipeline config");
        List<RosettaReport> reports = modelHelper.getReports(rosettaModels, filter.getModelNamespaceRegex(), filter.getExcluded());
        List<PipelineModel> reportPipelines = reports.stream()
                .map(this::createReportPipelineModel)
                .collect(Collectors.toList());
        reportPipelines.forEach(p -> testPackConfigWriter.writeConfigFile(writePath, REPORT_CONFIG_PATH, p.getId(), p));

        LOGGER.info("Report test pack config");
        List<TestPackModel> reportTestPacks = createReportTestPacks(reports, testPackDefs, filter, injector);
        reportTestPacks.forEach(testPackModel -> testPackConfigWriter.sortAndWriteConfigFile(writePath, REPORT_CONFIG_PATH, testPackModel));

        LOGGER.info("Projection pipeline config");
        List<Function> projections = getProjectionFunctions(rosettaModels, filter.getModelNamespaceRegex(), filter.getExcluded());
        List<PipelineModel> projectionPipelines = projections.stream()
                .map(f -> createProjectionPipelineModel(rosettaModels, f, filter.getExcluded()))
                .collect(Collectors.toList());
        projectionPipelines.forEach(p -> testPackConfigWriter.writeConfigFile(writePath, PROJECTION_CONFIG_PATH, p.getId(), p));

        LOGGER.info("Projection test pack config");
        List<TestPackModel> projectionTestPacks = createProjectionTestPacks(projectionPipelines, reports, reportTestPacks, outputSchemaMap, injector);
        projectionTestPacks.forEach(testPackModel -> testPackConfigWriter.sortAndWriteConfigFile(writePath, PROJECTION_CONFIG_PATH, testPackModel));
    }

    protected PipelineModel createReportPipelineModel(RosettaReport report) {
        ModelReportId reportId = toModelReportId(report);
        String name = reportId.joinRegulatoryReference(" / ", " ");
        return new PipelineModel(createPipelineId(reportId), name, getTransform(report), null, null);
    }

    protected String createPipelineId(ModelReportId reportId) {
        String formattedId = reportId.joinRegulatoryReference("-").toLowerCase();
        return String.format("pipeline-%s-%s", TransformType.REPORT.name().toLowerCase(), formattedId);
    }

    protected PipelineModel.Transform getTransform(RosettaReport report) {
        return new PipelineModel.Transform(TransformType.REPORT,
                modelHelper.toJavaClass(report),
                modelHelper.toJavaClass(report.getInputType().getType()),
                modelHelper.toJavaClass(report.getReportType())
        );
    }

    protected List<Function> getProjectionFunctions(List<RosettaModel> models, String namespaceRegex, Collection<Class<?>> excluded) {
        return new ArrayList<>(modelHelper.getFunctionsWithAnnotation(models, namespaceRegex, "projection", excluded));
    }

    protected PipelineModel createProjectionPipelineModel(List<RosettaModel> models, Function func, Collection<Class<?>> excluded) {
        String functionSimpleName = formatFunctionName(func.getName());
        String upstreamPipelineId = getProjectionUpstreamPipelineId(models, func, excluded);
        PipelineModel.Serialisation outputSerialisation = getXmlOutputSerialisation(func);
        return new PipelineModel(createPipelineId(func), functionSimpleName, getTransform(func), upstreamPipelineId, outputSerialisation);
    }

    protected String createPipelineId(Function func) {
        String formattedId = createIdSuffix(func.getName());
        return String.format("pipeline-%s-%s", TransformType.PROJECTION.name().toLowerCase(), formattedId);
    }

    protected String createIdSuffix(String functionName) {
        String functionSimpleName = formatFunctionName(functionName);
        return CaseFormat.UPPER_CAMEL
                .converterTo(CaseFormat.LOWER_HYPHEN)
                .convert(functionSimpleName.replace("Project_", ""));
    }

    protected String formatFunctionName(String functionName) {
        // TODO something better than this
        String simpleName = functionName.contains(".") ?
                functionName.substring(functionName.lastIndexOf(".") + 1) :
                functionName;
        return simpleName
                .replace("JFSA", "Jfsa")
                .replace("MAS", "Mas")
                .replace("ASIC", "Asic");
    }

    protected PipelineModel.Transform getTransform(Function func) {
        return new PipelineModel.Transform(TransformType.PROJECTION,
                modelHelper.toJavaClass(func),
                modelHelper.toJavaClass(modelHelper.getInputType(func)),
                modelHelper.toJavaClass(func.getOutput().getTypeCall().getType()));
    }

    protected String getProjectionUpstreamPipelineId(List<RosettaModel> models, Function func, Collection<Class<?>> excluded) {
        RosettaReport projectionUpstreamReport = modelHelper.getUpstreamReport(models, func, excluded);
        ModelReportId modelReportId = toModelReportId(projectionUpstreamReport);
        return createPipelineId(modelReportId);
    }

    protected PipelineModel.Serialisation getXmlOutputSerialisation(Function func) {
        RosettaType type = func.getOutput().getTypeCall().getType();
        return new PipelineModel.Serialisation(PipelineModel.Serialisation.Format.XML, formatIso20022XmlConfigPath(type));
    }

    protected String formatIso20022XmlConfigPath(RosettaType type) {
        String namespace = type.getModel().getName();
        String xmlConfigPath = namespace
                .replace("iso20022.", "")
                .replace(".", "-");
        return String.format("xml-config/%s-rosetta-xml-config.json", xmlConfigPath);
    }

    protected ModelReportId toModelReportId(RosettaReport rosettaReport) {
        return new ModelReportId(
                DottedPath.of(rosettaReport.getModel().getName()),
                rosettaReport.getRegulatoryBody().getBody().getName(),
                rosettaReport.getRegulatoryBody().getCorpusList().stream().map(RosettaNamed::getName).toArray(String[]::new));
    }

    protected List<TestPackModel> createReportTestPacks(List<RosettaReport> reports, List<TestPackDef> testPackDefs, TestPackFilter filter, Injector injector) {
        return testPackDefs.stream()
                .map(testPack -> {
                    List<RosettaReport> applicableReports = getApplicableReports(reports, testPack.getName(), testPack.getInputType(), filter.getReportTestPackMap(), filter.getTestPackReportMap());
                    return applicableReports.stream()
                            .map(report -> createReportTestPack(testPack, report, injector)).collect(Collectors.toList());
                }).flatMap(List::stream)
                .collect(Collectors.toList());
    }

    protected List<RosettaReport> getApplicableReports(List<RosettaReport> reports,
                                                       String testPackName,
                                                       String inputType,
                                                       ImmutableMultimap<Class<?>, String> reportIncludedTestPack,
                                                       ImmutableMultimap<String, Class<?>> testPackIncludedReport) {
        return reports.stream()
                .filter(r -> modelHelper.toJavaClass(r.getInputType().getType()).equals(inputType))
                .filter(r -> filterApplicableTestPacksForReport(testPackName, toClass(modelHelper.toJavaClass(r)), reportIncludedTestPack))
                .filter(r -> filterApplicableReportForTestPack(testPackName, toClass(modelHelper.toJavaClass(r)), testPackIncludedReport))
                .sorted(Comparator.comparing(r -> modelHelper.toJavaClass(r)))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    protected Class<? extends RosettaModelObject> toClass(String name) {
        try {
            return (Class<? extends RosettaModelObject>) Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean filterApplicableTestPacksForReport(String testPackName, Class<?> clazz, ImmutableMultimap<Class<?>, String> reportIdIncludedTestPack) {
        ImmutableCollection<String> applicableTestPacksForReport = reportIdIncludedTestPack.get(clazz);
        return applicableTestPacksForReport.isEmpty() || applicableTestPacksForReport.contains(testPackName);
    }

    protected boolean filterApplicableReportForTestPack(String testPackName, Class<?> clazz, ImmutableMultimap<String, Class<?>> testPackIncludedReportIds) {
        ImmutableCollection<Class<?>> applicableReportsForTestPack = testPackIncludedReportIds.get(testPackName);
        return applicableReportsForTestPack.isEmpty() || applicableReportsForTestPack.contains(clazz);
    }

    protected TestPackModel createReportTestPack(TestPackDef testPack, RosettaReport report, Injector injector) {
        ModelReportId reportId = toModelReportId(report);
        PipelineModel.Transform transform = getTransform(report);
        TestPackFunctionRunner functionRunner = functionRunnerProvider.create(transform, injector);

        String testPackName = testPack.getName();
        List<SampleModel> sampleModelLists = testPack.getInputPaths().stream()
                .map(targetLocation -> {
                    String fileName = FileNameProcessor.removeFilePrefix(targetLocation);
                    Path outputRelativePath = RegReportPaths.getDefault().getOutputRelativePath();
                    Path inputPath = RegReportPaths.getDefault().getInputRelativePath().resolve(directoryName(testPackName)).resolve(fileName);
                    Path outputPath = RegReportPaths.getReportExpectationFilePath(outputRelativePath, reportId, testPackName, inputPath);
                    String baseFileName = getBaseFileName(inputPath);
                    String displayName = baseFileName.replace("-", " ");

                    Pair<String, Assertions> result = null;
                    try {
                        result = functionRunner.run(inputPath);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException("Unable to apply report function. Invalid input path", e);
                    }
                    writeOutputFile(outputPath, result.left());
                    Assertions assertions = result.right();

                    return new SampleModel(baseFileName.toLowerCase(), displayName, inputPath.toString(), outputPath.toString(), assertions);
                })
                .sorted(Comparator.comparing(SampleModel::getId))
                .collect(Collectors.toList());

        String formattedFunctionName = reportId.joinRegulatoryReference("-").toLowerCase();
        return TestPackUtils.createTestPack(testPackName, TransformType.REPORT, formattedFunctionName, sampleModelLists);
    }

    private String getBaseFileName(Path inputPath) {
        return inputPath.getFileName().toString()
                .replace(".json", "")
                .replace("-report", "");
    }

    protected List<TestPackModel> createProjectionTestPacks(List<PipelineModel> projectionPipelines, List<RosettaReport> reports, List<TestPackModel> reportTestPacks, ImmutableMap<Class<?>, String> outputSchemaMap, Injector injector) {
        return projectionPipelines.stream()
                .map(p -> {
                    TestPackFunctionRunner functionRunner = functionRunnerProvider.create(p.getTransform(), p.getOutputSerialisation(), outputSchemaMap, injector);
                    return reportTestPacks.stream()
                            .filter(rtp -> rtp.getPipelineId().equals(p.getUpstreamPipelineId()))
                            .map(upstreamReportTestPack ->
                                    TestPackUtils.createTestPack(upstreamReportTestPack.getName(),
                                            TransformType.PROJECTION,
                                            createIdSuffix(p.getTransform().getFunction()),
                                            upstreamReportTestPack.getSamples().stream()
                                                    .map(s -> {
                                                        try {
                                                            return toProjectionSample(upstreamReportTestPack.getName(), getModelReportId(reports, upstreamReportTestPack.getPipelineId()), s, functionRunner);
                                                        } catch (MalformedURLException e) {
                                                            throw new RuntimeException("Unable to apply projection function. Invalid input path", e);
                                                        }
                                                    })
                                                    .collect(Collectors.toList()))
                            )
                            .collect(Collectors.toList());
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    protected ModelReportId getModelReportId(List<RosettaReport> reports, String pipelineId) {
        return reports.stream()
                .map(this::toModelReportId)
                .filter(reportId -> createPipelineId(reportId).equals(pipelineId))
                .findFirst()
                .orElseThrow();
    }

    protected SampleModel toProjectionSample(String testPackName, ModelReportId reportId, SampleModel reportSample, TestPackFunctionRunner functionRunner) throws MalformedURLException {
        Path projectionInputPath = Path.of(reportSample.getOutputPath());
        Path projectionTestPackPath = RegReportPaths.getOutputDataSetPath(PROJECTION_OUTPUT_PATH, reportId, testPackName);
        Path outputPath = getProjectionDataItemOutputPath(projectionTestPackPath, projectionInputPath);

        Pair<String, Assertions> result = functionRunner.run(projectionInputPath);
        writeOutputFile(outputPath, result.left());
        Assertions assertions = result.right();

        return new SampleModel(reportSample.getId(), reportSample.getName(), projectionInputPath.toString(), outputPath.toString(), assertions);
    }

    protected String directoryName(String name) {
        return name
                .replace(" ", "-")
                .replace("_", "-")
                .trim().toLowerCase();
    }

    private void writeOutputFile(Path outputPath, String serialisedOutput) {
        writeFile(TEST_WRITE_BASE_PATH.get().resolve(outputPath), serialisedOutput, true);
    }
}
