package com.regnosys.testing.reports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.regnosys.rosetta.common.reports.RegReportPaths;
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

	private final ObjectMapper writeMapper;
	private final RegReportPaths paths;

	public DescriptorWriter(ObjectMapper writeMapper) {
		this(writeMapper, RegReportPaths.getDefault());
	}

	public DescriptorWriter(ObjectMapper writeMapper, RegReportPaths paths) {
		this.writeMapper = writeMapper;
		this.paths = paths;
	}

	public void writeDescriptorFile(Path resourcesPath, ReportDataSet reportDataSet) {
		Path path = generateDescriptorPath(paths.getConfigRelativePath(), reportDataSet.getDataSetName());
		SimpleFilterProvider filterProvider = FilterProvider.getExpectedTypeFilter();

		try {
			ReportDataSet filteredReportDataSet = sortAndRemoveUningestedFiles(resourcesPath, reportDataSet);
			Path fullPath = resourcesPath.resolve(path);
			Files.createDirectories(fullPath.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(fullPath)) {
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

	private ReportDataSet sortAndRemoveUningestedFiles(Path resourcesPath, ReportDataSet reportDataSet) {
		List<ReportDataItem> data = reportDataSet.getData()
				.stream()
				.filter(datum -> dataItemInputExists(resourcesPath, datum))
				.sorted(Comparator.comparing(ReportDataItem::getName))
				.collect(Collectors.toList());

		return new ReportDataSet(reportDataSet.getDataSetName(),
				reportDataSet.getInputType(),
				reportDataSet.getApplicableReports(),
				data);
	}

	private boolean dataItemInputExists(Path resourcesPath, ReportDataItem datum) {
		String input = datum.getInput().toString();
		return Files.exists(resourcesPath.resolve(paths.getInputRelativePath()).resolve(input));
	}

	private List<ReportDataSet> readDescriptorFile(Path file) {
		try {
			return writeMapper.readValue(file.toFile(), new TypeReference<>() {
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
