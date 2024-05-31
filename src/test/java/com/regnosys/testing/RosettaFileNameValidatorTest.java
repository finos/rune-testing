package com.regnosys.testing;

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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
public class RosettaFileNameValidatorTest {

    @Test
    public void testFileNamesMatchNamespace() throws IOException {
        String modelShortName = "cdm";
        Path path = Paths.get("src/test/resources/rosetta-filename-validator");
        RosettaFileNameValidator ros = new RosettaFileNameValidator(modelShortName, path, null);
        ValidationReport validationReport = ros.validateFileNamesMatchNamespace();

        assertFalse(validationReport.getPassed());
        //file has a space before namespace hence namespace is read as null in the Validator class
        assertTrue(validationReport.getErrors().toString().contains("File name 'legalagreement-csa-func.rosetta' is not in line with namespace 'null'"), validationReport.getErrors().get(0));
        //No suffix of the 2nd file is incorrect
        assertTrue(validationReport.getErrors().toString().contains("No suffix for file 'noSuffix.rosetta'"), validationReport.getErrors().get(1));
        //inValid Suffix
        assertTrue(validationReport.getErrors().toString().contains("Suffix for file 'legalagreement-csa-funcg.rosetta'"), validationReport.getErrors().get(2));
        //namespace is missing model short name
        assertTrue(validationReport.getErrors().toString().contains("Namespace should start with model name 'cdm'"), validationReport.getErrors().get(3));
        //namespace is does not match filename as per rules
        assertTrue(validationReport.getErrors().toString().contains("Namespace should be 'cdm.legalagreement.csa'"), validationReport.getErrors().get(4));

        assertEquals(5, validationReport.getErrors().size(), validationReport.getErrors().size());
    }

    @Test
    public void parentFilesAreIgnored() throws IOException {
        String modelShortName = "cdm";
        Path path = Paths.get("src/test/resources/rosetta-filename-validator");
        Path parent = Paths.get("src/test/resources/rosetta-parent-filename-validator");
        RosettaFileNameValidator ros = new RosettaFileNameValidator(modelShortName, path, parent);
        ValidationReport validationReport = ros.validateFileNamesMatchNamespace();

        // legalagreement-csa-funcg.rosetta is ignored becasue it's in the parent model
        assertFalse(validationReport.getErrors().toString().contains("Suffix for file 'legalagreement-csa-funcg.rosetta'"));
    }

}
