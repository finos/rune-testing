package com.regnosys.testing.schemeimport;

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

import com.regnosys.rosetta.rosetta.RosettaEnumValue;
import com.regnosys.rosetta.rosetta.RosettaFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

class SchemeImporterTestHelperTest {

    private static final SchemeImporterTestHelper schemeImporterTestHelper = new SchemeImporterTestHelper();
    private static final RosettaFactory factory = RosettaFactory.eINSTANCE;

    @Test
    void compareSameEnumValuesExactMatch() {
        List<RosettaEnumValue> modelEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        List<RosettaEnumValue> codingSchemeEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        assertTrue(schemeImporterTestHelper.compareEnumValues(modelEnums, codingSchemeEnums, SchemeImporterTestHelper.EnumComparison.ExactMatch));
    }

    @Test
    void compareSameEnumValuesAdditiveMatch() {
        List<RosettaEnumValue> modelEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        List<RosettaEnumValue> codingSchemeEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        assertTrue(schemeImporterTestHelper.compareEnumValues(modelEnums, codingSchemeEnums, SchemeImporterTestHelper.EnumComparison.AdditiveMatch));
    }

    @Test
    void compareExtraModelEnumValuesExactMatch() {
        List<RosettaEnumValue> modelEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"), createRosettaEnumValue("value4"));
        List<RosettaEnumValue> codingSchemeEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        assertFalse(schemeImporterTestHelper.compareEnumValues(modelEnums, codingSchemeEnums, SchemeImporterTestHelper.EnumComparison.ExactMatch));
    }

    @Test
    void compareExtraModelEnumValuesAdditiveMatch() {
        List<RosettaEnumValue> modelEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"), createRosettaEnumValue("value4"));
        List<RosettaEnumValue> codingSchemeEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        assertTrue(schemeImporterTestHelper.compareEnumValues(modelEnums, codingSchemeEnums, SchemeImporterTestHelper.EnumComparison.AdditiveMatch));
    }

    @Test
    void compareExtraCodingSchemeEnumValuesExactMatch() {
        List<RosettaEnumValue> modelEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        List<RosettaEnumValue> codingSchemeEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"), createRosettaEnumValue("value4"));
        assertFalse(schemeImporterTestHelper.compareEnumValues(modelEnums, codingSchemeEnums, SchemeImporterTestHelper.EnumComparison.ExactMatch));
    }

    @Test
    void compareExtraCodingSchemeEnumValuesAdditiveMatch() {
        List<RosettaEnumValue> modelEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        List<RosettaEnumValue> codingSchemeEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"), createRosettaEnumValue("value4"));
        assertFalse(schemeImporterTestHelper.compareEnumValues(modelEnums, codingSchemeEnums, SchemeImporterTestHelper.EnumComparison.AdditiveMatch));
    }

    private RosettaEnumValue createRosettaEnumValue(String name) {
        RosettaEnumValue value = factory.createRosettaEnumValue();
        value.setName(name);
        value.setDefinition(name);
        return value;
    }

}
