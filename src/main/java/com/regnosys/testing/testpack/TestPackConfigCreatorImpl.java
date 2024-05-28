package com.regnosys.testing.testpack;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TestPackUtils;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaNamed;
import com.regnosys.rosetta.rosetta.RosettaReport;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Function;
import com.regnosys.testing.reports.FileNameProcessor;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import com.rosetta.model.lib.ModelReportId;
import com.rosetta.util.DottedPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.regnosys.rosetta.common.transform.TestPackUtils.*;
import static com.regnosys.testing.TestingExpectationUtil.TEST_WRITE_BASE_PATH;
import static com.regnosys.testing.projection.ProjectionPaths.getProjectionDataItemOutputPath;

public class TestPackConfigCreatorImpl implements TestPackConfigCreator {
    private final Logger LOGGER = LoggerFactory.getLogger(TestPackConfigCreatorImpl.class);

    @Inject
    private TestPackModelHelper modelHelper;

    /**
     * Generates pipeline and test-pack config files.
     *
     * @param rosettaPaths - list of folders that contain rosetta model files, e.g. "drr/rosetta"
     * @param filter       - provides filters to include or exclude
     * @param testPackDefs - provides list of test-pack information such as test pack name, input type and sample input paths
     */
    @Override
    public void createPipelineAndTestPackConfig(ImmutableList<String> rosettaPaths, TestPackFilter filter, List<TestPackDef> testPackDefs) {
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
        List<TestPackModel> reportTestPacks = createReportTestPacks(reports, testPackDefs, filter.getReportTestPackMap(), filter.getTestPackReportMap());
        reportTestPacks.forEach(testPackModel -> testPackConfigWriter.sortAndWriteConfigFile(writePath, REPORT_CONFIG_PATH, testPackModel));

        LOGGER.info("Projection pipeline config");
        List<Function> projections = getProjectionFunctions(rosettaModels, filter.getModelNamespaceRegex(), filter.getExcluded());
        List<PipelineModel> projectionPipelines = projections.stream()
                .map(f -> createProjectionPipelineModel(rosettaModels, f, filter.getExcluded()))
                .collect(Collectors.toList());
        projectionPipelines.forEach(p -> testPackConfigWriter.writeConfigFile(writePath, PROJECTION_CONFIG_PATH, p.getId(), p));

        LOGGER.info("Projection test pack config");
        List<TestPackModel> projectionTestPacks = createProjectionTestPacks(projectionPipelines, reports, reportTestPacks);
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
        String formattedId = CaseFormat.UPPER_CAMEL
                .converterTo(CaseFormat.LOWER_HYPHEN)
                .convert(functionSimpleName.replace("Project_", ""));
        return formattedId;
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

    protected List<TestPackModel> createReportTestPacks(List<RosettaReport> reports, List<TestPackDef> testPackDefs, ImmutableMultimap<Class<?>, String> reportIncludedTestPack, ImmutableMultimap<String, Class<?>> testPackIncludedReports) {
        return testPackDefs.stream()
                .map(testPack -> {
                    List<RosettaReport> applicableReports = getApplicableReports(reports, testPack.getName(), testPack.getInputType(), reportIncludedTestPack, testPackIncludedReports);
                    return applicableReports.stream()
                            .map(report -> {
                                List<String> targetLocations = testPack.getInputPaths();
                                return createTestPack(testPack.getName(), targetLocations, report);
                            }).collect(Collectors.toList());
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
                .filter(r -> filterApplicableTestPacksForReport(testPackName, getClass(modelHelper.toJavaClass(r)), reportIncludedTestPack))
                .filter(r -> filterApplicableReportForTestPack(testPackName, getClass(modelHelper.toJavaClass(r)), testPackIncludedReport))
                .sorted(Comparator.comparing(r -> modelHelper.toJavaClass(r)))
                .collect(Collectors.toList());
    }

    protected Class<?> getClass(String name) {
        try {
            return Class.forName(name);
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

    protected TestPackModel createTestPack(String testPackName, List<String> targetLocations, RosettaReport report) {
        ModelReportId reportId = toModelReportId(report);
        List<TestPackModel.SampleModel> sampleModelLists = targetLocations.stream()
                .map(targetLocation -> {
                    String fileName = FileNameProcessor.removeFilePrefix(targetLocation);
                    Path outputRelativePath = RegReportPaths.getDefault().getOutputRelativePath();
                    Path inputPath = RegReportPaths.getDefault().getInputRelativePath().resolve(directoryName(testPackName)).resolve(fileName);
                    Path outputPath = RegReportPaths.getReportExpectationFilePath(outputRelativePath, reportId, testPackName, inputPath);
                    String baseFileName = getBaseFileName(inputPath);
                    String displayName = baseFileName.replace("-", " ");
                    return new TestPackModel.SampleModel(baseFileName.toLowerCase(), displayName, inputPath.toString(), outputPath.toString(), new TestPackModel.SampleModel.Assertions(0, null, false));
                })
                .sorted(Comparator.comparing(TestPackModel.SampleModel::getId))
                .collect(Collectors.toList());

        String formattedFunctionName = reportId.joinRegulatoryReference("-").toLowerCase();
        return TestPackUtils.createTestPack(testPackName, TransformType.REPORT, formattedFunctionName, sampleModelLists);
    }

    protected String getBaseFileName(Path inputPath) {
        return inputPath.getFileName().toString()
                .replace(".json", "")
                .replace("-report", "");
    }

    protected List<TestPackModel> createProjectionTestPacks(List<PipelineModel> projectionPipelines, List<RosettaReport> reports, List<TestPackModel> reportTestPacks) {
        return projectionPipelines.stream()
                .map(p ->
                        reportTestPacks.stream()
                                .filter(rtp -> rtp.getPipelineId().equals(p.getUpstreamPipelineId()))
                                .map(upstreamReportTestPack ->
                                        TestPackUtils.createTestPack(upstreamReportTestPack.getName(),
                                                TransformType.PROJECTION,
                                                createIdSuffix(p.getTransform().getFunction()),
                                                upstreamReportTestPack.getSamples().stream()
                                                        .map(s -> toProjectionSample(upstreamReportTestPack.getName(), getModelReportId(reports, upstreamReportTestPack.getPipelineId()), s))
                                                        .collect(Collectors.toList()))
                                )
                                .collect(Collectors.toList()))
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

    protected TestPackModel.SampleModel toProjectionSample(String testPackName, ModelReportId reportId, TestPackModel.SampleModel reportSample) {
        String projectionInputPath = reportSample.getOutputPath();
        Path projectionTestPackPath = RegReportPaths.getOutputDataSetPath(PROJECTION_OUTPUT_PATH, reportId, testPackName);
        String outputPath = getProjectionDataItemOutputPath(projectionTestPackPath, Path.of(projectionInputPath)).toString();
        return new TestPackModel.SampleModel(reportSample.getId(), reportSample.getName(), projectionInputPath, outputPath, new TestPackModel.SampleModel.Assertions(0, false, false));
    }

    protected String directoryName(String name) {
        return name
                .replace(" ", "-")
                .replace("_", "-")
                .trim().toLowerCase();
    }
}
