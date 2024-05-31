package com.regnosys.testing.testpack;

/*-
 * #%L
 * Rosetta Testing
 * %%
 * Copyright (C) 2022 - 2024 REGnosys
 * %%
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
 * #L%
 */

import com.google.common.collect.ImmutableList;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.generator.java.types.JavaTypeTranslator;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaReport;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.AnnotationRef;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.Function;
import com.regnosys.rosetta.transgest.ModelLoader;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.util.DottedPath;
import com.rosetta.util.types.generated.GeneratedJavaClassService;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    public List<RosettaModel> loadRosettaModels(ImmutableList<String> rosettaPaths, ClassLoader classLoader) {
        return modelLoader.loadRosettaModels(ClassPathUtils.findPathsFromClassPath(
                        rosettaPaths,
                        ".*\\.rosetta",
                        Optional.empty(),
                        classLoader)
                .stream()
                .map(UrlUtils::toUrl));
    }

    @Override
    public List<RosettaReport> getReports(List<RosettaModel> models, String namespaceRegex, Collection<Class<?>> excluded) {
        Set<String> excludedClassNames = excluded.stream().map(Class::getName).collect(Collectors.toSet());
        return modelLoader.rosettaElements(models, RosettaReport.class)
                .stream()
                .filter(r -> filterNamespace(r.getModel(), namespaceRegex))
                .filter(r -> !excludedClassNames.contains(toJavaClass(r)))
                .collect(Collectors.toList());
    }

    @Override
    public List<Function> getFunctionsWithAnnotation(List<RosettaModel> models, String namespaceRegex, String annotation, Collection<Class<?>> excluded) {
        Set<String> excludedClassNames = excluded.stream().map(Class::getName).collect(Collectors.toSet());
        return modelLoader.rosettaElements(models, Function.class).stream()
                .filter(r -> filterNamespace(r.getModel(), namespaceRegex))
                .filter(f -> f.getAnnotations().stream()
                        .map(AnnotationRef::getAnnotation)
                        .anyMatch(a -> annotation.equals(a.getName())))
                .filter(r -> !excludedClassNames.contains(toJavaClass(r)))
                .collect(Collectors.toList());
    }

    @Override
    public RosettaType getInputType(Function func) {
        return emptyIfNull(func.getInputs()).stream()
                .map(inputAttr -> inputAttr.getTypeCall().getType())
                .findFirst()
                .orElseThrow();
    }

    @Override
    public RosettaReport getUpstreamReport(List<RosettaModel> models, Function func, Collection<Class<?>> excluded) {
        Data inputType = (Data) getInputType(func);
        return getReports(models, null, excluded).stream()
                .filter(r -> r.getReportType().equals(inputType))
                .findFirst()
                .orElseThrow();
    }

    @Override
    public String toJavaClass(Function function) {
        return javaTypeTranslator.toFunctionJavaClass(function).getCanonicalName().withDots();
    }

    @Override
    public String toJavaClass(RosettaReport report) {
        return javaTypeTranslator.toReportFunctionJavaClass(report).getCanonicalName().withDots();
    }

    @Override
    public String toJavaClass(RosettaType rosettaType) {
        return generatedJavaClassService.toJavaType(toModelSymbolId(rosettaType)).getCanonicalName().withDots();
    }

    private boolean filterNamespace(RosettaModel rosettaModel, String namespaceIncludeRegex) {
        return Optional.ofNullable(namespaceIncludeRegex)
                .map(regex -> rosettaModel.getName().matches(regex))
                .orElse(true);
    }

    private ModelSymbolId toModelSymbolId(RosettaType type) {
        DottedPath namespace = DottedPath.splitOnDots(type.getModel().getName());
        return new ModelSymbolId(namespace, type.getName());
    }
}
