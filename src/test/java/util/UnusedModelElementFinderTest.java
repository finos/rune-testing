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
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.transgest.ModelLoader;
import com.regnosys.rosetta.utils.ModelIdProvider;
import com.regnosys.testing.RosettaTestingInjectorProvider;
import com.rosetta.model.lib.ModelSymbolId;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.extensions.InjectionExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(InjectionExtension.class)
@InjectWith(RosettaTestingInjectorProvider.class)
public class UnusedModelElementFinderTest {
    @Inject
    private ModelLoader modelLoader;
    @Inject
    private ModelIdProvider modelIdProvider;

    @Test
    public void getListOfOrphanedTypes() {

        List<RosettaModel> models = modelLoader.loadRosettaModels(ClassPathUtils.findPathsFromClassPath(ImmutableList.of("model", "unused-model-element-finder"), ".*\\.rosetta", Optional.empty(), ClassPathUtils.class.getClassLoader()
                ).stream()
                .map(UrlUtils::toUrl));
        UnusedModelElementFinder unusedModelElementFinder = new UnusedModelElementFinder(models, modelIdProvider);

        unusedModelElementFinder.run();
        Set<String> listOfTypes = unusedModelElementFinder.getListOfTypes().stream().map(ModelSymbolId::toString).collect(Collectors.toSet());
        assertEquals(7, listOfTypes.size(), listOfTypes.toString());

        assertTrue(listOfTypes.contains("cdm.test.Test1"), "ListOfTypes should contain cdm.test.Test1");
        assertTrue(listOfTypes.contains("cdm.test.Test2"), "ListOfTypes should contain cdm.test.Test2");
        assertTrue(listOfTypes.contains("cdm.test.Test3"),"ListOfTypes should contain cdm.test.Test3");
        assertTrue(listOfTypes.contains("cdm.test.DirectionEnum"), "ListOfTypes should contain cdm.test.DirectionEnum");
        assertTrue(listOfTypes.contains("cdm.test.TestEnum2Unused"), "ListOfTypes should contain cdm.test.TestEnum2Unused");
        assertTrue(listOfTypes.contains("cdm.test.TestEnum3UsedInFuncOnly"), "ListOfTypes should contain cdm.test.TestEnum3UsedInFuncOnly");

        Set<String> listOfUsedTypes = unusedModelElementFinder.getListOfUsedTypes().stream().map(ModelSymbolId::toString).collect(Collectors.toSet());
        assertEquals(5, listOfUsedTypes.size(), listOfUsedTypes.toString());

        assertTrue(listOfUsedTypes.contains("cdm.test.Test1"), "ListOfUsedTypes should contain cdm.test.Test1");
        assertTrue(listOfUsedTypes.contains("cdm.test.Test2"), "ListOfUsedTypes should contain cdm.test.Test2");
        assertTrue(listOfUsedTypes.contains("cdm.test.Test3"), "ListOfUsedTypes should contain cdm.test.Test3");
        assertTrue(listOfUsedTypes.contains("cdm.test.DirectionEnum"), "ListOfUsedTypes should contain cdm.test.DirectionEnum");
        assertTrue(listOfUsedTypes.contains("cdm.test.TestEnum3UsedInFuncOnly"), "ListOfUsedTypes should contain cdm.test.TestEnum3UsedInFuncOnly");


        Set<String> listOfOrphanedTypes = unusedModelElementFinder.getListOfOrphanedTypes().stream().map(ModelSymbolId::toString).collect(Collectors.toSet());
        assertEquals(2, listOfOrphanedTypes.size(), listOfOrphanedTypes.toString());
        assertTrue(listOfOrphanedTypes.contains("cdm.test.TestEnum2Unused"), "ListOfOrphanedTypes should contain cdm.test.TestEnum2Unused");
        assertTrue(listOfOrphanedTypes.contains("cdm.test.Test4Unused"), "ListOfOrphanedTypes should contain cdm.test.Test4Unused");

        Set<String> listOfDeprecatedTypes = unusedModelElementFinder.getListOfDeprecatedTypes().stream().map(ModelSymbolId::toString).collect(Collectors.toSet());
        assertEquals(1, listOfDeprecatedTypes.size(), listOfDeprecatedTypes.toString());
        assertTrue(listOfDeprecatedTypes.contains("cdm.test.Test3"), "ListOfDeprecatedTypes should contain cdm.test.Test3");

    }
}
