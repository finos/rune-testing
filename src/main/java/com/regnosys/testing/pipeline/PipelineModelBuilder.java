package com.regnosys.testing.pipeline;

import com.google.common.collect.ImmutableMap;
import com.regnosys.rosetta.common.transform.PipelineModel;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class PipelineModelBuilder {

    private final FunctionNameHelper helper;

    @Inject
    public PipelineModelBuilder(FunctionNameHelper helper) {
        this.helper = helper;
    }

    public List<PipelineModel> createPipelineModels(PipelineTree pipelineTree) {
        return pipelineTree.getNodeList().stream()
                .map(modelBuilder -> build(modelBuilder, pipelineTree.getPipelineTreeConfig()))
                .collect(Collectors.toList());
    }

    public PipelineModel build(PipelineNode modelBuilder, PipelineTreeConfig config) {

        String inputType = helper.getInputType(modelBuilder.getFunction());
        String outputType = helper.getOutputType(modelBuilder.getFunction());
        String outputSerialisationConfigPath = config.getXmlConfigMap().get(helper.getFuncMethod(modelBuilder.getFunction()).getReturnType());
        String name = helper.getName(modelBuilder.getFunction());

        // assume XML for now.
        PipelineModel.Serialisation outputSerialisation = outputSerialisationConfigPath == null ? null :
                new PipelineModel.Serialisation(PipelineModel.Serialisation.Format.XML, outputSerialisationConfigPath);

        String pipelineId = modelBuilder.id(config.isStrictUniqueIds());
        String upstreamPipelineId = modelBuilder.upstreamId(config.isStrictUniqueIds());

        return new PipelineModel(pipelineId,
                name,
                new PipelineModel.Transform(modelBuilder.getTransformType(), modelBuilder.getFunction().getName(), inputType, outputType),
                upstreamPipelineId, outputSerialisation);
    }

}
