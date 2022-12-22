package com.regnosys.testing;

import com.regnosys.rosetta.RosettaRuntimeModule;
import com.regnosys.testing.reports.ReportUtil;

public class RosettaTestingModule extends RosettaRuntimeModule {
    public Class<? extends ReportUtil> bindReportUtil() {
        return ReportUtil.class;
    }
}
