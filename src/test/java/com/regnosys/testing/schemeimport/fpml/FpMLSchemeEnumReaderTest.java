package com.regnosys.testing.schemeimport.fpml;

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

import com.regnosys.testing.schemeimport.fpml.FpMLSchemeEnumReader;
import com.regnosys.testing.schemeimport.fpml.FpMLSchemeHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FpMLSchemeEnumReaderTest {

    private static final String SCHEME_VALUE = "CNY-Quarterly 7 day Repo Non Deliverable Swap Rate-TRADITION-Reference\n" +
            "                Banks";
    private static final String SCHEME_DESCRIPTION = "Per 2006 ISDA Definitions or Annex to the 2000 ISDA Definitions, Section\n" +
            "                7.1 Rate Options, as amended and supplemented through the date on which parties\n" +
            "                enter into the relevant transaction.";

    @Test
    void encodeValue() {
        FpMLSchemeEnumReader reader = new FpMLSchemeEnumReader(mock(FpMLSchemeHelper.class));
        String value = reader.encodeValue(SCHEME_VALUE);
        assertEquals("CNY_Quarterly_7_day_Repo_Non_Deliverable_Swap_Rate_TRADITION_Reference_Banks", value);
    }

    @Test
    void encodeDisplayName() {
        FpMLSchemeEnumReader reader = new FpMLSchemeEnumReader(mock(FpMLSchemeHelper.class));
        String displayName = reader.encodeDisplayName(SCHEME_VALUE);
        assertEquals("CNY-Quarterly 7 day Repo Non Deliverable Swap Rate-TRADITION-Reference Banks",
                displayName);
    }

    @Test
    void encodeDescription() {
        FpMLSchemeEnumReader reader = new FpMLSchemeEnumReader(mock(FpMLSchemeHelper.class));
        String description = reader.encodeDescription(SCHEME_DESCRIPTION);
        assertEquals("Per 2006 ISDA Definitions or Annex to the 2000 ISDA Definitions, Section 7.1 Rate " +
                        "Options, as amended and supplemented through the date on which parties enter into the " +
                        "relevant transaction.",
                description);
    }
}
