package com.regnosys.testing.reports;

import com.google.common.collect.ImmutableList;
import com.regnosys.rosetta.common.reports.RegReportIdentifier;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.rosetta.RosettaBlueprintReport;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaNamed;
import com.regnosys.rosetta.transgest.ModelLoader;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReportUtil {
    @Inject
    private ModelLoader modelLoader;

    public List<RegReportIdentifier> loadRegReportIdentifier(ImmutableList<String> rosettaFolderPathNames) {
        List<RosettaModel> models = modelLoader.loadRosettaModels(ClassPathUtils.findPathsFromClassPath(
                        rosettaFolderPathNames,
                        ".*\\.rosetta",
                        Optional.empty(),
                        ClassPathUtils.class.getClassLoader())
                .stream()
                .map(UrlUtils::toUrl));

        return modelLoader.rosettaElements(models, RosettaBlueprintReport.class).stream()
                .map(rosettaReport -> new RegReportIdentifier(rosettaReport.getRegulatoryBody().getBody().getName(),
                        rosettaReport.getRegulatoryBody().getCorpuses().stream().map(RosettaNamed::getName).collect(Collectors.toList()),
                        rosettaReport.name(),
                        String.format("%s.blueprint.%sBlueprintReport", rosettaReport.getModel().getName(), rosettaReport.name()))).collect(Collectors.toList());
    }
}