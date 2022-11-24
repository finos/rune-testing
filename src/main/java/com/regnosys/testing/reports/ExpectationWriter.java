package com.regnosys.testing.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.regnosys.rosetta.common.reports.RegReportIdentifier;
import com.regnosys.rosetta.common.serialisation.reportdata.ExpectedResultField;
import com.regnosys.rosetta.common.serialisation.reportdata.ReportDataItem;
import com.regnosys.rosetta.common.serialisation.reportdata.ReportDataSet;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ExpectationWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpectationWriter.class);

    public final ObjectMapper noExpectationWriteMapper;
    public final ObjectMapper writeMapper;

	public ExpectationWriter(ObjectMapper noExpectationWriteMapper,
							 ObjectMapper writeMapper) {
		this.noExpectationWriteMapper = noExpectationWriteMapper;
		this.writeMapper = writeMapper;
	}

    public void writeMissingExpectationsFilesForAllDescriptors(Path cdmOutFolder, Map<Path, List<ReportDataSet>> descriptorFiles) {
        Map<Path, List<ReportDataSet>> newDescriptorFiles = new HashMap<>();
        for (Map.Entry<Path, List<ReportDataSet>> descriptorFileEntry : descriptorFiles.entrySet()) {
            var descriptorFile = descriptorFileEntry.getValue();
            var descriptorFilePath = descriptorFileEntry.getKey();
            List<ReportDataSet> newDescriptorFile = new ArrayList<>();
            newDescriptorFiles.put(descriptorFilePath, newDescriptorFile);
            for (ReportDataSet reportDataSet : descriptorFile) {
                List<ReportDataItem> newReportDataItems = new ArrayList<>();
                var newReportDataSet = new ReportDataSet(reportDataSet.getDataSetName(), reportDataSet.getInputType(), reportDataSet.getApplicableReports(), newReportDataItems);
                newDescriptorFile.add(newReportDataSet);
                for (ReportDataItem reportDataItem : reportDataSet.getData()) {
                    Object expected = reportDataItem.getExpected();
                    ReportDataItem newReportDataItem;
                    if (expected == null) {
                        String input = (String) reportDataItem.getInput();
                        var newExpected = "expected/" + input.replaceFirst("\\.json$", "-expected.json");
                        newReportDataItem = new ReportDataItem(reportDataItem.getName(), reportDataItem.getInput(), newExpected);
                        writeNewExpectationFile(cdmOutFolder.resolve(newExpected));
                    } else {
                        newReportDataItem = new ReportDataItem(reportDataItem.getName(), reportDataItem.getInput(), reportDataItem.getExpected());
                    }
                    newReportDataItems.add(newReportDataItem);
                }
            }
        }
        writeUpdatedDescriptorFiles(newDescriptorFiles);
    }

    private void writeUpdatedDescriptorFiles(Map<Path, List<ReportDataSet>> newDescriptorFiles) {
		SimpleFilterProvider filterProvider = FilterProvider.getExpectedTypeFilter();

        for (Map.Entry<Path, List<ReportDataSet>> descriptorEntry : newDescriptorFiles.entrySet()) {
            Path path = descriptorEntry.getKey();
            List<ReportDataSet> descriptor = descriptorEntry.getValue();
            try {
                Files.createDirectories(path.getParent());
                try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    writer.write(
                            writeMapper.writer(filterProvider)
                                    .withDefaultPrettyPrinter()
                                    .writeValueAsString(descriptor)
                    );
                    LOGGER.info("Writing descriptor file: {}", path);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void writeNewExpectationFile(Path newExpected) {
        try {
            Files.createDirectories(newExpected.getParent());
            Files.write(newExpected, "{}".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

	public Table<String, String, List<ExpectedResultField>> groupByExpectationFileAndReportName(List<ExpectationResult> expectationResult, List<ReportDataSet> reportDataSetDefinitions) {
		ImmutableTable.Builder<String, String, List<ExpectedResultField>> builder = ImmutableTable.builder();
		expectationResult.forEach(result ->
				expectedFileName(result.getDatasetName(), result.getUseCaseName(), result.getIdentifier(), reportDataSetDefinitions)
						.ifPresent(s -> builder.put(s, result.getIdentifier().getName(), result.getActualReportFields())));
		return builder.build();
	}

	@NotNull
	private Optional<String> expectedFileName(String datasetName, String useCaseName, RegReportIdentifier identifier, List<ReportDataSet> reportDataSetDefinitions) {
		return reportDataSetDefinitions.stream()
				.filter(ds -> ds.getDataSetName().equals(datasetName))
				.filter(r -> r.getApplicableReports().contains(identifier.getGeneratedJavaClassName()) || r.getApplicableReports().isEmpty())
				.map(ReportDataSet::getData)
				.flatMap(Collection::stream)
				.filter(reportDataItem -> reportDataItem.getName().equals(useCaseName))
				.map(ReportDataItem::getExpected)
				.filter(Objects::nonNull)
				.map(Object::toString)
				.findFirst();
	}
}
