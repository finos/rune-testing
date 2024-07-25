package com.regnosys.testing.pipeline;

import java.util.List;

public class PipelineTree {

    private final List<PipelineNode> nodeList;
    private final PipelineTreeConfig pipelineTreeConfig;

    public PipelineTree(List<PipelineNode> nodeList, PipelineTreeConfig pipelineTreeConfig) {
        this.nodeList = nodeList;
        this.pipelineTreeConfig = pipelineTreeConfig;
    }

    public List<PipelineNode> getNodeList() {
        return nodeList;
    }

    public PipelineTreeConfig getPipelineTreeConfig() {
        return pipelineTreeConfig;
    }
}
