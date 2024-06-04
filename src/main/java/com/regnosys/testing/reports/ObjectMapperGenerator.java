package com.regnosys.testing.reports;

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
