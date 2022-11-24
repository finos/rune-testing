package com.regnosys.testing.reports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.regnosys.rosetta.common.serialisation.reportdata.ReportDataItem;
import com.regnosys.rosetta.common.serialisation.reportdata.ReportDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DescriptorWriter {
	private static final Logger LOGGER = LoggerFactory.getLogger(DescriptorWriter.class);

	public final ObjectMapper writeMapper;

	public DescriptorWriter(ObjectMapper writeMapper) {
		this.writeMapper = writeMapper;
	}

	public void writeDescriptorFile(Path outFolder, ReportDataSet reportDataSet) {
		Path path = generateDescriptorPath(outFolder, reportDataSet.getDataSetName());
		SimpleFilterProvider filterProvider = FilterProvider.getExpectedTypeFilter();

		try {
			ReportDataSet filteredReportDataSet = sortAndRemoveUningestedFiles(outFolder, reportDataSet);
			Files.createDirectories(path.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(path)) {
				writer.write(
						writeMapper.writer(filterProvider)
								.withDefaultPrettyPrinter()
								.writeValueAsString(List.of(filteredReportDataSet))
				);
				LOGGER.info("Writing descriptor file: {}", path);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private ReportDataSet sortAndRemoveUningestedFiles(Path outFolder, ReportDataSet reportDataSet) {
		List<ReportDataItem> data = reportDataSet.getData()
				.stream()
				.filter(datum -> dataItemInputExists(outFolder, datum))
				.sorted(Comparator.comparing(ReportDataItem::getName))
				.collect(Collectors.toList());

		return new ReportDataSet(reportDataSet.getDataSetName(),
				reportDataSet.getInputType(),
				reportDataSet.getApplicableReports(),
				data);
	}

	private boolean dataItemInputExists(Path outFolder, ReportDataItem datum) {
		String input = datum.getInput().toString();
		return Files.exists(outFolder.resolve(input));
	}

	private List<ReportDataSet> readDescriptorFile(Path file) {
		try {
			return writeMapper.readValue(file.toFile(), new TypeReference<List<ReportDataSet>>() {
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<Path, List<ReportDataSet>> readAllDescriptorFiles(Path descriptorFileParentDirectory) {
		try {
			return Files.walk(descriptorFileParentDirectory)
					.filter(p -> p.getFileName().toString().endsWith("-descriptor.json"))
					.collect(Collectors.toMap(
							path -> path,
							this::readDescriptorFile
					));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Path generateDescriptorPath(Path outFolder, String dataSetName) {
		String descriptorFileName = dataSetName
				.replace(" ", "-")
				.toLowerCase()
				+ "-regulatory-reporting-data-descriptor.json";

		return outFolder.resolve(FileNameProcessor.sanitizeFileName(descriptorFileName));
	}
}
