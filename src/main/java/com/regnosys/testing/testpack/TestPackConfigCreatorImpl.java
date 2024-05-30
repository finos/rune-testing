package com.regnosys.testing.testpack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.Resources;
import com.google.inject.Injector;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.hashing.ReferenceResolverProcessStep;
import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TestPackUtils;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaNamed;
import com.regnosys.rosetta.rosetta.RosettaReport;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Function;
import com.regnosys.testing.RosettaTestingInjectorProvider;
import com.regnosys.testing.reports.FileNameProcessor;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import com.rosetta.model.lib.ModelReportId;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import com.rosetta.util.DottedPath;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.regnosys.rosetta.common.transform.TestPackUtils.*;
import static com.regnosys.testing.TestingExpectationUtil.TEST_WRITE_BASE_PATH;
import static com.regnosys.testing.projection.ProjectionPaths.getProjectionDataItemOutputPath;

public class TestPackConfigCreatorImpl implements TestPackConfigCreator {
    private final Logger LOGGER = LoggerFactory.getLogger(TestPackConfigCreatorImpl.class);

    @Inject
    private TestPackModelHelper modelHelper;
    @Inject
    RosettaTypeValidator typeValidator;
    @Inject
    ReferenceConfig referenceConfig;

    private static final ObjectMapper JSON_OBJECT_MAPPER = RosettaObjectMapper.getNewRosettaObjectMapper();

    private final static ObjectWriter JSON_OBJECT_WRITER =
            JSON_OBJECT_MAPPER
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .writerWithDefaultPrettyPrinter();

    /**
     * Generates pipeline and test-pack config files.
     *
     * @param rosettaPaths - list of folders that contain rosetta model files, e.g. "drr/rosetta"
     * @param filter       - provides filters to include or exclude
     * @param testPackDefs - provides list of test-pack information such as test pack name, input type and sample input paths
     */
    @Override
    public void createPipelineAndTestPackConfig(ImmutableList<String> rosettaPaths, TestPackFilter filter, List<TestPackDef> testPackDefs, ImmutableMap<String, String> functionSchemaMap) {
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
        List<TestPackModel> projectionTestPacks = createProjectionTestPacks(projectionPipelines, reports, reportTestPacks, functionSchemaMap);
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

    protected List<TestPackModel> createReportTestPacks(List<RosettaReport> reports, List<TestPackDef> testPackDefs, ImmutableMultimap<Class<?>, String> reportIncludedTestPack, ImmutableMultimap<String, Class<?>> testPackIncludedReports) {
        Injector injector = new RosettaTestingInjectorProvider().getInjector();

        return testPackDefs.stream()
                .map(testPack -> {
                    List<RosettaReport> applicableReports = getApplicableReports(reports, testPack.getName(), testPack.getInputType(), reportIncludedTestPack, testPackIncludedReports);
                    return applicableReports.stream()
                            .map(report -> {
                                List<String> targetLocations = testPack.getInputPaths();
                                return createTestPack(testPack.getName(), targetLocations, report, injector);
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

    protected Class<? extends RosettaModelObject> getClass(String name) {
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

    protected TestPackModel createTestPack(String testPackName, List<String> targetLocations, RosettaReport report,
                                           Injector injector) {
        ModelReportId reportId = toModelReportId(report);
        PipelineModel.Transform transform = getTransform(report);
        Class<? extends RosettaModelObject> inputType = getClass(transform.getInputType());
//        Class<? extends RosettaModelObject> outputType = getClass(transform.getOutputType());
        Class<?> functionClass = getClass(transform.getFunction());

        List<TestPackModel.SampleModel> sampleModelLists = targetLocations.stream()
                .map(targetLocation -> {
                    String fileName = FileNameProcessor.removeFilePrefix(targetLocation);
                    Path outputRelativePath = RegReportPaths.getDefault().getOutputRelativePath();
                    Path inputPath = RegReportPaths.getDefault().getInputRelativePath().resolve(directoryName(testPackName)).resolve(fileName);
                    Path outputPath = RegReportPaths.getReportExpectationFilePath(outputRelativePath, reportId, testPackName, inputPath);
                    String baseFileName = getBaseFileName(inputPath);
                    String displayName = baseFileName.replace("-", " ");
                    return generateReportTestPackSample(
                            baseFileName.toLowerCase(),
                            displayName,
                            inputPath.toString(),
                            outputPath.toString(),
                            inputType,
                            functionClass,
                            injector,
                            transform,
                            null,
                            null);
                })
                .sorted(Comparator.comparing(TestPackModel.SampleModel::getId))
                .collect(Collectors.toList());

        String formattedFunctionName = reportId.joinRegulatoryReference("-").toLowerCase();
        return TestPackUtils.createTestPack(testPackName, TransformType.REPORT, formattedFunctionName, sampleModelLists);
    }

    private <IN extends RosettaModelObject, OUT extends RosettaModelObject> TestPackModel.SampleModel generateReportTestPackSample(
            String baseFileName,
            String displayName,
            String inputPath,
            String outputPath,
            Class<IN> inputType,
            Class<?> functionClass,
            Injector injector,
            PipelineModel.Transform transform,
            PipelineModel pipelineModel,
            ImmutableMap<String, String> functionSchemaMap) {
        ObjectWriter objectWriter = TestPackUtils.getObjectWriter(pipelineModel.getOutputSerialisation()).orElse(JSON_OBJECT_WRITER);
        URL inputFileUrl = getInputFileUrl(inputPath);
        assert inputFileUrl != null;
        IN input = readFile(inputFileUrl, JSON_OBJECT_MAPPER, inputType);
        IN resolvedInput = resolveReferences(input);

        Object functionInstance = injector.getInstance(functionClass);
        try {
            Method evaluateMethod = functionClass.getMethod("evaluate", inputType);

            OUT output = (OUT) evaluateMethod.invoke(functionInstance, resolvedInput);

            // validation failures
            ValidationReport validationReport = typeValidator.runProcessStep(output.getType(), output);
            validationReport.logReport();
            int actualValidationFailures = validationReport.validationFailures().size();

            TestPackModel.SampleModel.Assertions assertions;
            if (transform.getType().equals(TransformType.PROJECTION)) {
                assertions = processProjectionSchemaValidationFailures(transform, functionSchemaMap, objectWriter, output, actualValidationFailures);
            } else {
                assertions = new TestPackModel.SampleModel.Assertions(actualValidationFailures, null, false);
            }
            return new TestPackModel.SampleModel(baseFileName.toLowerCase(), displayName, inputPath, outputPath, assertions);

        } catch (NoSuchMethodException e) {
            LOGGER.error("Evaluate method unsuccessfully invoked. Method not  found. ");
            throw new RuntimeException(e);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            LOGGER.error("Exception occurred running sample creation", e);
            return new TestPackModel.SampleModel(baseFileName.toLowerCase(), displayName, inputPath, outputPath, new TestPackModel.SampleModel.Assertions(0, null, true));
        }
    }

    private <OUT extends RosettaModelObject> TestPackModel.SampleModel.@NotNull Assertions processProjectionSchemaValidationFailures(PipelineModel.Transform transform, ImmutableMap<String, String> functionSchemaMap, ObjectWriter objectWriter, OUT output, int actualValidationFailures) throws JsonProcessingException, SAXException {
        TestPackModel.SampleModel.Assertions assertions;
        //it is projection so we look up the function class, and find the relevant schema
        URL schemaUrl = Resources.getResource(Objects.requireNonNull(functionSchemaMap.get(transform.getFunction())));
        String serializedOutput = objectWriter.writeValueAsString(output);
        Boolean schemaValidationFailure = isSchemaValidationFailure(schemaUrl, serializedOutput);
        assertions = new TestPackModel.SampleModel.Assertions(actualValidationFailures, schemaValidationFailure, false);
        return assertions;
    }

    //TODO: to be used for projection
    private Boolean isSchemaValidationFailure(URL xsdSchema, String actualXml) throws SAXException {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // required to process xml elements with an maxOccurs greater than 5000 (rather than unbounded)
        schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        Schema schema = schemaFactory.newSchema(xsdSchema);
        Validator xsdValidator = schema.newValidator();
        if (xsdValidator == null) {
            return null;
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(actualXml.getBytes(StandardCharsets.UTF_8))) {
            xsdValidator.validate(new StreamSource(inputStream));
            return true;
        } catch (SAXException e) {
            LOGGER.error("Schema validation failed: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static URL getInputFileUrl(String inputFile) {
        try {
            return Resources.getResource(inputFile);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private <T extends RosettaModelObject> T resolveReferences(T modelObject) {
        RosettaModelObjectBuilder builder = modelObject.toBuilder();
        new ReferenceResolverProcessStep(referenceConfig).runProcessStep(modelObject.getType(), builder);
        return (T) builder.build();
    }

    private String getBaseFileName(Path inputPath) {
        return inputPath.getFileName().toString()
                .replace(".json", "")
                .replace("-report", "");
    }

    protected List<TestPackModel> createProjectionTestPacks(List<PipelineModel> projectionPipelines, List<RosettaReport> reports, List<TestPackModel> reportTestPacks, ImmutableMap<String, String> functionSchemaMap) {
        Injector injector = new RosettaTestingInjectorProvider().getInjector();
        return projectionPipelines.stream()
                .map(p ->
                        reportTestPacks.stream()
                                .filter(rtp -> rtp.getPipelineId().equals(p.getUpstreamPipelineId()))
                                .map(upstreamReportTestPack ->
                                        TestPackUtils.createTestPack(upstreamReportTestPack.getName(),
                                                TransformType.PROJECTION,
                                                createIdSuffix(p.getTransform().getFunction()),
                                                upstreamReportTestPack.getSamples().stream()
                                                        .map(s -> toProjectionSample(upstreamReportTestPack.getName(), getModelReportId(reports, upstreamReportTestPack.getPipelineId()), s, p, injector, functionSchemaMap))
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

    protected TestPackModel.SampleModel toProjectionSample(String testPackName, ModelReportId reportId, TestPackModel.SampleModel reportSample, PipelineModel pipelineModel, Injector injector, ImmutableMap<String, String> functionSchemaMap) {
        String projectionInputPath = reportSample.getOutputPath();
        Path projectionTestPackPath = RegReportPaths.getOutputDataSetPath(PROJECTION_OUTPUT_PATH, reportId, testPackName);
        String outputPath = getProjectionDataItemOutputPath(projectionTestPackPath, Path.of(projectionInputPath)).toString();
        Class<? extends RosettaModelObject> inputType = getClass(pipelineModel.getTransform().getInputType());
//        Class<? extends RosettaModelObject> outputType = getClass(transform.getOutputType());
        Class<?> functionClass = getClass(pipelineModel.getTransform().getFunction());
//        return new TestPackModel.SampleModel(reportSample.getId(), reportSample.getName(), projectionInputPath, outputPath, new TestPackModel.SampleModel.Assertions(0, false, false));
        return generateReportTestPackSample(
                reportSample.getId(),
                reportSample.getName(),
                projectionInputPath,
                outputPath,
                inputType,
                functionClass,
                injector,
                pipelineModel.getTransform(),
                pipelineModel,
                functionSchemaMap);
    }

    protected String directoryName(String name) {
        return name
                .replace(" ", "-")
                .replace("_", "-")
                .trim().toLowerCase();
    }
}
