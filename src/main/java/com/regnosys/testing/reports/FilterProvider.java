package com.regnosys.testing.reports;

import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

public class FilterProvider {
	public static SimpleFilterProvider getExpectedTypeFilter() {
		SimpleBeanPropertyFilter reportDataSetFilter = SimpleBeanPropertyFilter.serializeAllExcept("expectedType");
		return new SimpleFilterProvider()
				.addFilter("reportDataSetFilter", reportDataSetFilter);
	}
}
