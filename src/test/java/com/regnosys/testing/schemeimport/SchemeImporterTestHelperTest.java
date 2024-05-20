package com.regnosys.testing.schemeimport;

import com.google.inject.Inject;
import com.regnosys.rosetta.rosetta.RosettaEnumValue;
import com.regnosys.rosetta.rosetta.RosettaEnumeration;
import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.impl.RosettaEnumValueImpl;
import com.regnosys.rosetta.transgest.ModelLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemeImporterTestHelperTest {

    private static final SchemeImporterTestHelper schemeImporterTestHelper = new SchemeImporterTestHelper();
    private static final RosettaFactory factory = RosettaFactory.eINSTANCE;

    @Test
    void compareSameEnumValuesDevMode() {
        List<RosettaEnumValue> modelEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        List<RosettaEnumValue> codingSchemeEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        assertTrue(schemeImporterTestHelper.compareEnumValues(modelEnums, codingSchemeEnums, true));
    }

    @Test
    void compareSameEnumValuesProdMode() {
        List<RosettaEnumValue> modelEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        List<RosettaEnumValue> codingSchemeEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        assertTrue(schemeImporterTestHelper.compareEnumValues(modelEnums, codingSchemeEnums, false));
    }

    @Test
    void compareExtraEnumValuesDevMode() {
        List<RosettaEnumValue> modelEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"), createRosettaEnumValue("value4"));
        List<RosettaEnumValue> codingSchemeEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        assertFalse(schemeImporterTestHelper.compareEnumValues(modelEnums, codingSchemeEnums, true));
    }

    @Test
    void compareExtraEnumValuesProdMode() {
        List<RosettaEnumValue> modelEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"), createRosettaEnumValue("value4"));
        List<RosettaEnumValue> codingSchemeEnums = List.of(createRosettaEnumValue("value1"), createRosettaEnumValue("value2"), createRosettaEnumValue("value3"));
        assertTrue(schemeImporterTestHelper.compareEnumValues(modelEnums, codingSchemeEnums, false));
    }

    private RosettaEnumValue createRosettaEnumValue(String name) {
        RosettaEnumValue value = factory.createRosettaEnumValue();
        value.setName(name);
        value.setDefinition(name);
        return value;
    }

}