package com.regnosys.testing.reports;

import com.regnosys.rosetta.common.reports.RegReportIdentifier;
import com.regnosys.rosetta.common.serialisation.reportdata.ExpectedResultField;

import java.util.Comparator;
import java.util.List;

@Deprecated
public class ExpectationResult implements Comparable<ExpectationResult> {

	private final String datasetName;
	private final String useCaseName;
	private final RegReportIdentifier identifier;
	private final List<ExpectedResultField> actualReportFields;

	private final Comparator<ExpectationResult> comparator = Comparator
			.comparing(ExpectationResult::getDatasetName)
			.thenComparing(ExpectationResult::getUseCaseName);

	public ExpectationResult(String datasetName, String useCaseName, RegReportIdentifier identifier, List<ExpectedResultField> actualReportFields) {
		this.datasetName = datasetName;
		this.useCaseName = useCaseName;
		this.identifier = identifier;
		this.actualReportFields = actualReportFields;
	}

	public RegReportIdentifier getIdentifier() {
		return identifier;
	}

	public List<ExpectedResultField> getActualReportFields() {
		return actualReportFields;
	}

	public String getDatasetName() {
		return datasetName;
	}

	public String getUseCaseName() {
		return useCaseName;
	}

	@Override
	public int compareTo(ExpectationResult o) {
		return comparator.compare(this, o);
	}
}