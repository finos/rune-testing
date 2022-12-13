package com.regnosys.testing.reports;

import com.google.common.collect.ImmutableList;
import com.regnosys.rosetta.common.reports.RegReportIdentifier;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.rosetta.RosettaBlueprintReport;
import com.regnosys.rosetta.rosetta.RosettaNamed;
import com.regnosys.rosetta.transgest.ModelLoader;
import com.regnosys.rosetta.transgest.ModelLoaderImpl;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReportUtil {

    public static List<RegReportIdentifier> loadRegReportIdentifier(ImmutableList<String> rosettaFolderPathNames) {
        ModelLoader modelLoader = new ModelLoaderImpl(ClassPathUtils.findPathsFromClassPath(
                        rosettaFolderPathNames,
                        ".*\\.rosetta",
                        Optional.empty(),
                        ClassPathUtils.class.getClassLoader())
                .stream()
                .map(UrlUtils::toUrl)
                .toArray(URL[]::new));

        return modelLoader.rosettaElements(RosettaBlueprintReport.class).stream()
                .map(rosettaReport -> new RegReportIdentifier(rosettaReport.getRegulatoryBody().getBody().getName(),
                        rosettaReport.getRegulatoryBody().getCorpuses().stream().map(RosettaNamed::getName).collect(Collectors.toList()),
                        rosettaReport.name(),
                        String.format("%s.blueprint.%sBlueprintReport", rosettaReport.getModel().getName(), rosettaReport.name()))).collect(Collectors.toList());
    }
}