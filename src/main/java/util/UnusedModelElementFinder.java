package util;

/*-
 * ===============
 * Rune Testing
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

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.rosetta.*;
import com.regnosys.rosetta.rosetta.simple.AnnotationRef;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.Function;
import com.regnosys.rosetta.transgest.ModelLoader;
import com.regnosys.testing.RosettaTestingInjectorProvider;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Util needs to determine if types are used or not
 */
public class UnusedModelElementFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnusedModelElementFinder.class);
    private final Set<String> listOfTypes = new HashSet<>();
    private final Set<String> listOfUsedTypes = new HashSet<>();
    private final Set<String> listOfOrphanedTypes = new HashSet<>();

    private final Set<String> listOfDeprecatedTypes = new HashSet<>();
    private final List<RosettaModel> models;

    public UnusedModelElementFinder(List<RosettaModel> models) {
        this.models = models;
    }

    public static void main(String[] args) {
        Injector injector = new RosettaTestingInjectorProvider().getInjector();
        ModelLoader loader = injector.getInstance(ModelLoader.class);
        List<RosettaModel> models = loader.loadRosettaModels(ClassPathUtils.findRosettaFilePaths().stream().map(UrlUtils::toUrl));

        new UnusedModelElementFinder(models).run();
    }

    public void run() {

        generateTypesList();

        LOGGER.trace("{} Types found in Model ", listOfTypes.size());
        LOGGER.trace("{} Types are used within Model", listOfUsedTypes.size());
        listOfOrphanedTypes.addAll(listOfTypes);
        listOfOrphanedTypes.removeAll(listOfUsedTypes);

        LOGGER.trace("out of which {} are now orphaned types as listed below:", listOfOrphanedTypes.size());

        // Arrays.stream(listOfOrphanedTypes.toArray()).sorted()
        //   .forEach(System.out::println);

    }

    private String getQualifiedName(RosettaType type) {
        return type.getNamespace().getName() + "." + type.getName();
    }

    private void generateTypesList() {

        for (RosettaModel model : models) {
            LOGGER.trace("Processing namespace {}, containing {} model elements", model.getName(), model.getElements().size());

            model.getElements().stream()
                    .filter(Data.class::isInstance)
                    .map(Data.class::cast)
                    .forEach(dataType -> {
                        LOGGER.trace(" Processing data type: {}", getQualifiedName(dataType));
                        listOfTypes.add(getQualifiedName(dataType));
                        if(null!=dataType.getSuperType())
                            listOfUsedTypes.add(getQualifiedName(dataType.getSuperType()));
                        TreeIterator<EObject> eObjectTreeIterator = dataType.eAllContents();
                        updateUsedTypes(eObjectTreeIterator);

                        updateDeprecatedTypes(dataType);
                    });

            model.getElements().stream()
                    .filter(RosettaEnumeration.class::isInstance)
                    .map(RosettaEnumeration.class::cast)
                    .forEach(enumeration -> {
                        LOGGER.trace("Processing enumeration type {}", getQualifiedName(enumeration));
                        listOfTypes.add(getQualifiedName(enumeration));
                    });

            model.getElements().stream()
                    .filter(Function.class::isInstance)
                    .map(Function.class::cast)
                    .forEach(function -> {
                        LOGGER.trace(" Processing function types {}.{}", function.getNamespace().getName(), function.getName());
                        // listOfTypes.add((function.getModel().getName().concat(".")).concat(function.getName()));

                        TreeIterator<EObject> eObjectTreeIterator = function.eAllContents();
                        updateUsedTypes(eObjectTreeIterator);
                    });
        }

    }

    private void updateDeprecatedTypes(Data dataType) {

        EList<AnnotationRef> annotations = dataType.getAnnotations();
        annotations
                .forEach(annotationRef -> {
                    if(annotationRef.getAnnotation().getName().equals("deprecated")){
                        listOfDeprecatedTypes.add(getQualifiedName(dataType));
                    }
                });

    }

    private void updateUsedTypes(TreeIterator<EObject> eObjectTreeIterator) {
        ArrayList<EObject> elements = Lists.newArrayList(eObjectTreeIterator);

        elements.stream()
                .filter(TypeCall.class::isInstance)
                .map(TypeCall.class::cast)
                .forEach(typeCall -> listOfUsedTypes.add(getQualifiedName(typeCall.getType())));

    }

    public Set<String> getListOfTypes() {
        return listOfTypes;
    }

    public Set<String> getListOfUsedTypes() {
        return listOfUsedTypes;
    }

    public Set<String> getListOfOrphanedTypes() {
        return listOfOrphanedTypes;
    }

    public Set<String> getListOfDeprecatedTypes() {
        return listOfDeprecatedTypes;
    }
}
