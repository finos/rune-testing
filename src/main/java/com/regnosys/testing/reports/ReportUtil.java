package com.regnosys.testing.reports;

import com.google.common.collect.ImmutableList;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaNamed;
import com.regnosys.rosetta.rosetta.RosettaReport;
import com.regnosys.rosetta.transgest.ModelLoader;
import com.rosetta.model.lib.ModelReportId;
import com.rosetta.util.DottedPath;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReportUtil {
    @Inject
    private ModelLoader modelLoader;

    public List<ModelReportId> loadReportIdentifiers(ImmutableList<String> rosettaFolderPathNames) {
        List<RosettaModel> models = modelLoader.loadRosettaModels(ClassPathUtils.findPathsFromClassPath(
                        rosettaFolderPathNames,
                        ".*\\.rosetta",
                        Optional.empty(),
                        ClassPathUtils.class.getClassLoader())
                .stream()
                .map(UrlUtils::toUrl));

        return modelLoader.rosettaElements(models, RosettaReport.class).stream()
                .map(rosettaReport -> new ModelReportId(
                        DottedPath.of(rosettaReport.getModel().getName()),
                        rosettaReport.getRegulatoryBody().getBody().getName(),
                        rosettaReport.getRegulatoryBody().getCorpusList().stream().map(RosettaNamed::getName).toArray(String[]::new)))
                .collect(Collectors.toList());
    }
}