package com.regnosys.testing.reports;

/*-
 * ===============
 * Rosetta Testing
 * ===============
 * Copyright (C) 2022 - 2024 REGnosys
 * ===============
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ===============
 */

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

@Deprecated // is this used?
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
