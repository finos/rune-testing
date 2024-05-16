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
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.generator.java.types.JavaTypeTranslator;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaNamed;
import com.regnosys.rosetta.rosetta.RosettaReport;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.AnnotationRef;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.Function;
import com.regnosys.rosetta.transgest.ModelLoader;
import com.regnosys.testing.reports.FileNameProcessor;
import com.rosetta.model.lib.ModelReportId;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.util.DottedPath;
import com.rosetta.util.types.generated.GeneratedJavaClassService;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.rosetta.common.transform.PipelineModel.Serialisation;
import static com.regnosys.rosetta.common.transform.PipelineModel.Transform;
import static com.regnosys.rosetta.common.transform.TestPackUtils.PROJECTION_OUTPUT_PATH;
import static com.regnosys.testing.projection.ProjectionPaths.getProjectionDataItemOutputPath;
import static com.rosetta.util.CollectionUtils.emptyIfNull;

public class TestPackModelHelperImpl implements TestPackModelHelper {
    @Inject
    private ModelLoader modelLoader;
    @Inject
    private JavaTypeTranslator javaTypeTranslator;

    private final GeneratedJavaClassService generatedJavaClassService;

    public TestPackModelHelperImpl() {
        this.generatedJavaClassService = new GeneratedJavaClassService();
    }

    @Override
    public List<RosettaReport> getReports(List<RosettaModel> models, String namespaceRegex) {
        return modelLoader.rosettaElements(models, RosettaReport.class)
                .stream()
                .filter(r -> filterNamespace(r.getModel(), namespaceRegex))
                .collect(Collectors.toList());
    }

    @Override
    public PipelineModel createReportPipelineModel(RosettaReport report) {
        ModelReportId reportId = toModelReportId(report);
        String name = reportId.joinRegulatoryReference(" / ", " ");
        return new PipelineModel(createPipelineId(reportId), name, getTransform(report), null, null);
    }

    public String createPipelineId(ModelReportId reportId) {
        String formattedId = reportId.joinRegulatoryReference("-").toLowerCase();
        return String.format("pipeline-%s-%s", TransformType.REPORT.name().toLowerCase(), formattedId);
    }

    private Transform getTransform(RosettaReport report) {
        return new Transform(TransformType.REPORT,
                toJavaClass(report),
                toJavaClass(report.getInputType().getType()),
                toJavaClass(report.getReportType())
        );
    }

    @Override
    public List<Function> getProjectionFunctions(List<RosettaModel> models, String namespaceRegex) {
        return getFunctionsWithAnnotation(models, namespaceRegex, "projection")
                .collect(Collectors.toList());
    }

    private Stream<Function> getFunctionsWithAnnotation(List<RosettaModel> models, String namespaceRegex, String annotation) {
        return modelLoader.rosettaElements(models, Function.class).stream()
                .filter(r -> filterNamespace(r.getModel(), namespaceRegex))
                .filter(f -> f.getAnnotations().stream()
                        .map(AnnotationRef::getAnnotation)
                        .anyMatch(a -> annotation.equals(a.getName())));
    }

    @Override
    public PipelineModel createProjectionPipelineModel(List<RosettaModel> models, Function func) {
        String functionSimpleName = formatFunctionName(func.getName());
        String upstreamPipelineId = getProjectionUpstreamPipelineId(models, func);
        Serialisation outputSerialisation = getXmlOutputSerialisation(func);
        return new PipelineModel(createPipelineId(func), functionSimpleName, getTransform(func), upstreamPipelineId, outputSerialisation);
    }

    private String createPipelineId(Function func) {
        String formattedId = createIdSuffix(func.getName());
        return String.format("pipeline-%s-%s", TransformType.PROJECTION.name().toLowerCase(), formattedId);
    }

    public String createIdSuffix(String functionName) {
        String functionSimpleName = formatFunctionName(functionName);
        String formattedId = CaseFormat.UPPER_CAMEL
                .converterTo(CaseFormat.LOWER_HYPHEN)
                .convert(functionSimpleName.replace("Project_", ""));
        return formattedId;
    }

    private String formatFunctionName(String functionName) {
        // TODO something better than this
        String simpleName = functionName.contains(".") ?
                functionName.substring(functionName.lastIndexOf(".") + 1) :
                functionName;
        return simpleName
                .replace("JFSA", "Jfsa")
                .replace("MAS", "Mas")
                .replace("ASIC", "Asic");
    }

    private Transform getTransform(Function func) {
        return new Transform(TransformType.PROJECTION,
                toJavaClass(func),
                toJavaClass(getInputType(func)),
                toJavaClass(func.getOutput().getTypeCall().getType()));
    }

    private RosettaType getInputType(Function func) {
        return emptyIfNull(func.getInputs()).stream()
                .map(inputAttr -> inputAttr.getTypeCall().getType())
                .findFirst()
                .orElseThrow();
    }

    private String getProjectionUpstreamPipelineId(List<RosettaModel> models, Function func) {
        Data inputType = (Data) getInputType(func);
        return getReports(models, null).stream()
                .filter(r -> r.getReportType().equals(inputType))
                .map(this::toModelReportId)
                .map(this::createPipelineId)
                .findFirst()
                .orElseThrow();
    }

    private Serialisation getXmlOutputSerialisation(Function func) {
        RosettaType type = func.getOutput().getTypeCall().getType();
        return new Serialisation(Serialisation.Format.XML, formatIso20022XmlConfigPath(type));
    }

    private String formatIso20022XmlConfigPath(RosettaType type) {
        String namespace = type.getModel().getName();
        String xmlConfigPath = namespace
                .replace("iso20022.", "")
                .replace(".", "-");
        return String.format("xml-config/%s-rosetta-xml-config.json", xmlConfigPath);
    }

    protected boolean filterNamespace(RosettaModel rosettaModel, String namespaceIncludeRegex) {
        return Optional.ofNullable(namespaceIncludeRegex)
                .map(regex -> rosettaModel.getName().matches(regex))
                .orElse(true);
    }

    public String toJavaClass(Function function) {
        return javaTypeTranslator.toFunctionJavaClass(function).getCanonicalName().withDots();
    }

    public String toJavaClass(RosettaReport report) {
        return javaTypeTranslator.toReportFunctionJavaClass(report).getCanonicalName().withDots();
    }

    public String toJavaClass(RosettaType rosettaType) {
        return generatedJavaClassService.toJavaType(toModelSymbolId(rosettaType)).getCanonicalName().withDots();
    }

    private ModelSymbolId toModelSymbolId(RosettaType type) {
        DottedPath namespace = DottedPath.splitOnDots(type.getModel().getName());
        return new ModelSymbolId(namespace, type.getName());
    }

    public ModelReportId toModelReportId(RosettaReport rosettaReport) {
        return new ModelReportId(
                DottedPath.of(rosettaReport.getModel().getName()),
                rosettaReport.getRegulatoryBody().getBody().getName(),
                rosettaReport.getRegulatoryBody().getCorpusList().stream().map(RosettaNamed::getName).toArray(String[]::new));
    }

    @Override
    public List<TestPackModel> createReportTestPacks(List<RosettaReport> reports, TestPackDef testPackDef, ImmutableMultimap<Class<?>, String> reportIncludedTestPack, ImmutableMultimap<String, Class<?>> testPackIncludedReports) {
        return testPackDef.getTestPacks().stream()
                .map(testPack -> {
                    List<RosettaReport> applicableReports = getApplicableReports(reports, testPack.getName(), testPack.getInputType(), reportIncludedTestPack, testPackIncludedReports);
                    return applicableReports.stream()
                            .map(report -> {
                                List<String> targetLocations = testPackDef.getTestPackInputPaths(testPack.getName());
                                return createTestPack(testPack.getName(), targetLocations, report);
                            }).collect(Collectors.toList());
                }).flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<RosettaReport> getApplicableReports(List<RosettaReport> reports,
                                                    String testPackName,
                                                    String inputType,
                                                    ImmutableMultimap<Class<?>, String> reportIncludedTestPack,
                                                    ImmutableMultimap<String, Class<?>> testPackIncludedReport) {
        return reports.stream()
                .filter(r -> toJavaClass(r.getInputType().getType()).equals(inputType))
                .filter(r -> filterApplicableTestPacksForReport(testPackName, getClass(toJavaClass(r)), reportIncludedTestPack))
                .filter(r -> filterApplicableReportForTestPack(testPackName, getClass(toJavaClass(r)), testPackIncludedReport))
                .sorted(Comparator.comparing(r -> toJavaClass(r)))
                .collect(Collectors.toList());
    }

    private static Class<?> getClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean filterApplicableTestPacksForReport(String testPackName, Class<?> clazz, ImmutableMultimap<Class<?>, String> reportIdIncludedTestPack) {
        ImmutableCollection<String> applicableTestPacksForReport = reportIdIncludedTestPack.get(clazz);
        return applicableTestPacksForReport.isEmpty() || applicableTestPacksForReport.contains(testPackName);
    }

    private static boolean filterApplicableReportForTestPack(String testPackName, Class<?> clazz, ImmutableMultimap<String, Class<?>> testPackIncludedReportIds) {
        ImmutableCollection<Class<?>> applicableReportsForTestPack = testPackIncludedReportIds.get(testPackName);
        return applicableReportsForTestPack.isEmpty() || applicableReportsForTestPack.contains(clazz);
    }

    private TestPackModel createTestPack(String testPackName, List<String> targetLocations, RosettaReport report) {
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

    private static String getBaseFileName(Path inputPath) {
        return inputPath.getFileName().toString()
                .replace(".json", "")
                .replace("-report", "");
    }

    @Override
    public List<TestPackModel> createProjectionTestPacks(List<PipelineModel> projectionPipelines, List<RosettaReport> reports, List<TestPackModel> reportTestPacks) {
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

    private ModelReportId getModelReportId(List<RosettaReport> reports, String pipelineId) {
        return reports.stream()
                .map(this::toModelReportId)
                .filter(reportId -> createPipelineId(reportId).equals(pipelineId))
                .findFirst()
                .orElseThrow();
    }

    private TestPackModel.SampleModel toProjectionSample(String testPackName, ModelReportId reportId, TestPackModel.SampleModel reportSample) {
        String projectionInputPath = reportSample.getOutputPath();
        Path projectionTestPackPath = RegReportPaths.getOutputDataSetPath(PROJECTION_OUTPUT_PATH, reportId, testPackName);
        String outputPath = getProjectionDataItemOutputPath(projectionTestPackPath, Path.of(projectionInputPath)).toString();
        return new TestPackModel.SampleModel(reportSample.getId(), reportSample.getName(), projectionInputPath, outputPath, new TestPackModel.SampleModel.Assertions(0, false, false));
    }

    static String directoryName(String name) {
        return name
                .replace(" ", "-")
                .replace("_", "-")
                .trim().toLowerCase();
    }

    @Override
    public List<RosettaModel> loadRosettaModels(ImmutableList<String> rosettaFolderPathNames) {
        List<RosettaModel> models = modelLoader.loadRosettaModels(ClassPathUtils.findPathsFromClassPath(
                        rosettaFolderPathNames,
                        ".*\\.rosetta",
                        Optional.empty(),
                        ClassPathUtils.class.getClassLoader())
                .stream()
                .map(UrlUtils::toUrl));
        return models;
    }
}