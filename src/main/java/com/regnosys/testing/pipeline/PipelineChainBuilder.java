package com.regnosys.testing.pipeline;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;
import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.regnosys.testing.reports.ObjectMapperGenerator;
import com.rosetta.model.lib.functions.RosettaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.testing.TestingExpectationUtil.TEST_WRITE_BASE_PATH;

public class PipelineChainBuilder {
    private final Logger LOGGER = LoggerFactory.getLogger(PipelineChainBuilder.class);

    private final FunctionNameHelper helper;

    @Inject
    public PipelineChainBuilder(FunctionNameHelper helper) {
        this.helper = helper;
    }

    void writePipelines(PipelineFunctionChain pipelineChainFunction) throws IOException {
        if (TEST_WRITE_BASE_PATH.isEmpty()) {
            LOGGER.error("TEST_WRITE_BASE_PATH not set");
            return;
        }

        Path resourcesPath = TEST_WRITE_BASE_PATH.get();

        List<PipelineModel> allPipelines = createAllPipelines(pipelineChainFunction);
        ObjectWriter objectWriter = ObjectMapperGenerator.createWriterMapper().writerWithDefaultPrettyPrinter();
        for (PipelineModel pipeline : allPipelines) {
            Path writePath = Files.createDirectories(resourcesPath.resolve(pipeline.getTransform().getType().getResourcePath()).resolve("config"));
            Path writeFile = writePath.resolve(pipeline.getId() + ".json");
            objectWriter.writeValue(writeFile.toFile(), pipeline);
        }
    }

    public List<PipelineModel> createAllPipelines(PipelineFunctionChain pipelineChainFunction) {
        Class<? extends RosettaFunction> startingFunction = pipelineChainFunction.getStartingFunction();
        TransformType startingTransformType = pipelineChainFunction.getStartingTransformType();

        PipelineModelBuilder startingPipeline = new PipelineModelBuilder(startingTransformType)
                .withFunction(startingFunction);

        List<PipelineModelBuilder> pipelineModelBuilders = downstreamPipelines(pipelineChainFunction, startingPipeline);
        return Stream.concat(Stream.of(startingPipeline), pipelineModelBuilders.stream())
                .map(modelBuilder -> build(modelBuilder, pipelineChainFunction.getXmlConfigMap()))
                .collect(Collectors.toList());
    }

    private List<PipelineModelBuilder> downstreamPipelines(PipelineFunctionChain pipelineChainFunction, PipelineModelBuilder currentPipeline) {
        TransformType downstreamTransformType = pipelineChainFunction.getDownstreamTransformType(currentPipeline.getFunction());
        if (downstreamTransformType == null) {
            return List.of();
        }
        List<PipelineModelBuilder> downstreamPipelines = createDownstreamPipelineAndLinkUpstream(pipelineChainFunction, currentPipeline, downstreamTransformType);
        List<PipelineModelBuilder> pipelineModelBuilders = new ArrayList<>(downstreamPipelines);
        for (PipelineModelBuilder downstreamPipeline : downstreamPipelines) {
            List<PipelineModelBuilder> recurse = downstreamPipelines(pipelineChainFunction, downstreamPipeline);
            pipelineModelBuilders.addAll(recurse);
        }
        return pipelineModelBuilders;
    }

    private List<PipelineModelBuilder> createDownstreamPipelineAndLinkUpstream(PipelineFunctionChain pipelineChainFunction, PipelineModelBuilder currentPipeline, TransformType transformType) {
        List<Class<? extends RosettaFunction>> downstreamFunctions = pipelineChainFunction.getDownstreamFunctions(currentPipeline.getFunction());
        return new PipelineModelBuilder(transformType)
                .linkWithUpstream(currentPipeline)
                .withFunctions(downstreamFunctions);
    }

    private PipelineModel build(PipelineModelBuilder modelBuilder, ImmutableMap<Class<?>, String> xmlConfigMap) {
        String upstreamPipelineId = modelBuilder.upstreamId();

        String inputType = helper.getInputType(modelBuilder.getFunction());
        String outputType = helper.getOutputType(modelBuilder.getFunction());
        String outputSerialisationConfigPath = xmlConfigMap.get(helper.getFuncMethod(modelBuilder.getFunction()).getReturnType());
        String name = helper.getName(modelBuilder.getTransformType(), modelBuilder.getFunction());

        // assume XML for now.
        PipelineModel.Serialisation outputSerialisation = outputSerialisationConfigPath == null ? null :
                new PipelineModel.Serialisation(PipelineModel.Serialisation.Format.XML, outputSerialisationConfigPath);

        return new PipelineModel(modelBuilder.id(),
                name,
                new PipelineModel.Transform(modelBuilder.getTransformType(), modelBuilder.getFunction().getName(), inputType, outputType),
                upstreamPipelineId, outputSerialisation);
    }

    private class PipelineModelBuilder {

        private final TransformType transformType;
        private Class<? extends RosettaFunction> function;
        private PipelineModelBuilder upstreamPipelineModelBuilder;

        private PipelineModelBuilder(TransformType transformType) {
            this.transformType = transformType;
        }

        private PipelineModelBuilder(TransformType transformType, Class<? extends RosettaFunction> function, PipelineModelBuilder upstreamPipelineModelBuilder) {
            this.transformType = transformType;
            this.function = function;
            this.upstreamPipelineModelBuilder = upstreamPipelineModelBuilder;
        }

        // To be used
        private String getInputPath() {
            if (upstreamPipelineModelBuilder != null) {
                return String.format("%s/output/%s", upstreamPipelineModelBuilder.getTransformType().getResourcePath(), upstreamPipelineModelBuilder.getFunction().getSimpleName().toLowerCase());
            }
            return String.format("%s/input/%s", transformType.getResourcePath(), function.getSimpleName().toLowerCase());
        }

        // To be used
        private String getOutputPath() {
            return String.format("%s/output/%s", transformType.getResourcePath(), function.getSimpleName().toLowerCase());
        }

        private List<PipelineModelBuilder> withFunctions(List<Class<? extends RosettaFunction>> function) {
            return function.stream()
                    .map(f -> new PipelineModelBuilder(transformType, f, upstreamPipelineModelBuilder))
                    .collect(Collectors.toList());
        }

        private PipelineModelBuilder withFunction(Class<? extends RosettaFunction> function) {
            this.function = function;
            return this;
        }

        private PipelineModelBuilder linkWithUpstream(PipelineModelBuilder upstreamPipelineModelBuilder) {
            this.upstreamPipelineModelBuilder = upstreamPipelineModelBuilder;
            return this;
        }

        private Class<? extends RosettaFunction> getFunction() {
            return function;
        }

        private TransformType getTransformType() {
            return transformType;
        }

        private String nameXXX() {
            String name = function.getSimpleName().toLowerCase();
            return (upstreamPipelineModelBuilder == null) ?
                    String.format("%s", name) :
                    String.format("%s-%s", upstreamPipelineModelBuilder.nameXXX(), name);
        }

        private String id() {
            return String.format("pipeline-%s-%s", getTransformType().name().toLowerCase(), idSuffix());
        }


        private String upstreamId() {
            return (upstreamPipelineModelBuilder == null) ? null :
                    String.format("pipeline-%s-%s", upstreamPipelineModelBuilder.getTransformType().name().toLowerCase(), upstreamPipelineModelBuilder.upstreamIdSuffix());
        }

        private String upstreamIdSuffix() {
            return (upstreamPipelineModelBuilder == null) ? idSuffix() :
                    String.format("%s-%s", upstreamPipelineModelBuilder.upstreamIdSuffix(), helper.readableId(getFunction()));
        }

        private String idSuffix() {
            return (upstreamPipelineModelBuilder == null) ? helper.readableId(getFunction()) :
                    String.format("%s-%s", upstreamPipelineModelBuilder.idSuffix(), helper.readableId(getFunction()));
        }
    }
}
