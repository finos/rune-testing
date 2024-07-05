package com.regnosys.testing.pipeline;

import com.regnosys.rosetta.common.transform.PipelineModel;
import com.regnosys.rosetta.common.transform.TransformType;
import com.rosetta.model.lib.functions.RosettaFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterables.getOnlyElement;
import static org.junit.jupiter.api.Assertions.*;

class PipelineModelChainBuilderTest {

    @Inject
    private PipelineChainBuilder pipelineChainBuilder;

    @BeforeEach
    void setUp() {
        pipelineChainBuilder = new PipelineChainBuilder(new FunctionNameHelper());
    }

    @Test
    void buildChain() {
        List<PipelineModel> allPipelines = pipelineChainBuilder
                .createAllPipelines(PipelineFunctionChain
                        .starting(TransformType.ENRICH, Start.class)
                        .add(Start.class, TransformType.REPORT, Middle.class)
                        .add(Middle.class, TransformType.PROJECTION, End.class));

        Map<TransformType, List<PipelineModel>> byTransformType = allPipelines.stream().collect(Collectors.groupingBy(x -> x.getTransform().getType()));

        PipelineModel startPipeline = getOnlyElement(byTransformType.get(TransformType.ENRICH));
        PipelineModel middlePipeline = getOnlyElement(byTransformType.get(TransformType.REPORT));
        PipelineModel endPipeline = getOnlyElement(byTransformType.get(TransformType.PROJECTION));

        assertPipelineModel(startPipeline, TransformType.ENRICH, Start.class, "pipeline-enrich-start", null);
        assertPipelineModel(middlePipeline, TransformType.REPORT, Middle.class, "pipeline-report-start-middle", "pipeline-enrich-start");
        assertPipelineModel(endPipeline, TransformType.PROJECTION, End.class, "pipeline-projection-start-middle-end", "pipeline-report-start-middle");
    }

    @Test
    void buildTreeChain() {
        List<PipelineModel> allPipelines = pipelineChainBuilder
                .createAllPipelines(PipelineFunctionChain
                        .starting(TransformType.ENRICH, Start.class)
                        .add(Start.class, TransformType.REPORT, MiddleA.class)
                        .add(Start.class, TransformType.REPORT, MiddleB.class)
                        .add(MiddleA.class, TransformType.PROJECTION, EndA.class)
                        .add(MiddleA.class, TransformType.PROJECTION, EndB.class)
                        .add(MiddleB.class, TransformType.PROJECTION, EndA.class)
                        .add(MiddleB.class, TransformType.PROJECTION, EndB.class));

        Map<TransformType, List<PipelineModel>> byTransformType = allPipelines.stream().collect(Collectors.groupingBy(x -> x.getTransform().getType()));

        PipelineModel startPipeline = getOnlyElement(byTransformType.get(TransformType.ENRICH));
        List<PipelineModel> middlePipelines = byTransformType.get(TransformType.REPORT);
        assertEquals(2, middlePipelines.size());

        List<PipelineModel> endPipelines = byTransformType.get(TransformType.PROJECTION);
        assertEquals(4, endPipelines.size());

        assertPipelineModel(startPipeline, TransformType.ENRICH, Start.class, "pipeline-enrich-start", null);

        assertPipelineModel(middlePipelines.get(0), TransformType.REPORT, MiddleA.class, "pipeline-report-start-middle-a", "pipeline-enrich-start");
        assertPipelineModel(middlePipelines.get(1), TransformType.REPORT, MiddleB.class, "pipeline-report-start-middle-b", "pipeline-enrich-start");

        assertPipelineModel(endPipelines.get(0), TransformType.PROJECTION, EndA.class, "pipeline-projection-start-middle-a-end-a", "pipeline-report-start-middle-a");
        assertPipelineModel(endPipelines.get(1), TransformType.PROJECTION, EndB.class, "pipeline-projection-start-middle-a-end-b", "pipeline-report-start-middle-a");
        assertPipelineModel(endPipelines.get(2), TransformType.PROJECTION, EndA.class, "pipeline-projection-start-middle-b-end-a", "pipeline-report-start-middle-b");
        assertPipelineModel(endPipelines.get(3), TransformType.PROJECTION, EndB.class, "pipeline-projection-start-middle-b-end-b", "pipeline-report-start-middle-b");
    }

    private static void assertPipelineModel(PipelineModel pipelineModel, TransformType transformType, Class<? extends RosettaFunction> function, String pipelineId, String upstreamPipelineId) {
        assertEquals(transformType, pipelineModel.getTransform().getType(), "Wrong transform type");
        assertEquals(function.getName(), pipelineModel.getTransform().getFunction(), "Wrong function name");
        assertEquals(pipelineId, pipelineModel.getId(), "Wrong pipeline id");
        assertEquals(upstreamPipelineId, pipelineModel.getUpstreamPipelineId(), "Wrong upstream pipeline id");
    }

    static class Start implements RosettaFunction {
        public String evaluate(String foo) {
            return null;
        }
    }

    static class Middle implements RosettaFunction {
        public String evaluate(String foo) {
            return null;
        }
    }

    static class MiddleA implements RosettaFunction {
        public String evaluate(String foo) {
            return null;
        }
    }

    static class MiddleB implements RosettaFunction {
        public String evaluate(String foo) {
            return null;
        }
    }

    static class End implements RosettaFunction {
        public String evaluate(String foo) {
            return null;
        }
    }

    static class EndA implements RosettaFunction {
        public String evaluate(String foo) {
            return null;
        }
    }

    static class EndB implements RosettaFunction {
        public String evaluate(String foo) {
            return null;
        }
    }
}