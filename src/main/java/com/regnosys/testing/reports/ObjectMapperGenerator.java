package com.regnosys.testing.reports;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.serialisation.reportdata.ReportDataItem;
import com.regnosys.rosetta.common.serialisation.reportdata.ReportDataSet;

public interface ObjectMapperGenerator {
	@JsonFilter("reportDataSetFilter")
	class ReportDataSetMixin {}

	@JsonFilter("reportDataItemFilter")
	class ReportDataItemMixin {}

	static ObjectMapper createWriterMapper() {
		return createWriterMapper(false);
	}

	static ObjectMapper createWriterMapper(boolean addReportDataItemMixin) {
		ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper()
				.setSerializationInclusion(JsonInclude.Include.USE_DEFAULTS)
				.setSerializationInclusion(JsonInclude.Include.NON_NULL)
				.addMixIn(ReportDataSet.class, ReportDataSetMixin.class);
		if (addReportDataItemMixin) {
			mapper.addMixIn(ReportDataItem.class, ReportDataItemMixin.class);
		}
		return mapper;
	}
}
