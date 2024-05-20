package com.regnosys.testing.schemeimport;

import com.regnosys.testing.RosettaTestingModule;
import com.regnosys.testing.schemeimport.fpml.FpMLSchemeEnumReader;
import com.regnosys.testing.schemeimport.fpml.FpMLSchemeHelper;
import com.regnosys.testing.schemeimport.iso.currency.IsoCurrencySchemeEnumReader;

public class SchemeImportModule extends RosettaTestingModule {
    public Class<? extends AnnotatedRosettaEnumReader> bindAnnotatedRosettaEnumReader() {
        return AnnotatedRosettaEnumReader.class;
    }

    public Class<? extends FpMLSchemeEnumReader> bindFpMLSchemeEnumReader() {
        return FpMLSchemeEnumReader.class;
    }

    public Class<? extends RosettaResourceWriter> bindRosettaResourceWriter() {
        return RosettaResourceWriter.class;
    }

    public Class<? extends SchemeImporter> bindSchemeImporter() {
        return SchemeImporter.class;
    }

    public Class<? extends SchemeImporterTestHelper> bindSchemeImporterTestHelper() {
        return SchemeImporterTestHelper.class;
    }

    public Class<? extends FpMLSchemeHelper> bindFpMLSchemeHelper() {
        return FpMLSchemeHelper.class;
    }

    public Class<? extends IsoCurrencySchemeEnumReader> bindIsoCurrencySchemeEnumReader() {
        return IsoCurrencySchemeEnumReader.class;
    }
}
