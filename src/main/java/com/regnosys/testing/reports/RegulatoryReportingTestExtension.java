package com.regnosys.testing.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.regnosys.rosetta.common.reports.RegReport;
import com.regnosys.rosetta.common.reports.RegReportIdentifier;
import com.regnosys.rosetta.common.reports.RegReportUseCase;
import com.regnosys.rosetta.common.serialisation.lookup.JsonLookupDataLoader;
import com.regnosys.rosetta.common.serialisation.reportdata.*;
import com.regnosys.rosetta.common.util.ClassPathUtils;
import com.regnosys.rosetta.common.util.UrlUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@Deprecated
public class RegulatoryReportingTestExtension implements BeforeAllCallback, AfterAllCallback {
	private static final Logger LOGGER = LoggerFactory.getLogger(RegulatoryReportingTestExtension.class);

	public static final Path REGULATORY_REPORTING_RESOURCE_ROOT = Path.of("regulatory-reporting");
	public static final Path LOOKUP_FOLDER = Path.of("lookup");
	public static final Path DATA_FOLDER = Path.of("data");
	public static final Path SRC_DATA_FOLDER = Path.of("..", "rosetta-source", "src", "main", "resources").resolve(REGULATORY_REPORTING_RESOURCE_ROOT).resolve(DATA_FOLDER);

    private final ReportUtil reportUtil;

	private final Path regReportingRoot;
	private final List<ExpectationResult> collectedExpectationResult = new ArrayList<>();

	private URL reportDescriptorLocation;
	private boolean writeOutputFiles = false;
	private List<ReportDataSet> reportDataSetDefinitions;
	private URL lookupDescriptorLocation;
	private DescriptorWriter descriptorWriter;
	private ExpectationWriter expectationWriter;

	private Multimap<String, String> exclusionList;

	public RegulatoryReportingTestExtension(ReportUtil reportUtil) {
		this(reportUtil, REGULATORY_REPORTING_RESOURCE_ROOT);
	}

	public RegulatoryReportingTestExtension(ReportUtil reportUtil, Path regReportingRoot) {
		this.regReportingRoot = regReportingRoot;
        this.reportUtil = reportUtil;
	}

	public RegulatoryReportingTestExtension writeOutputFiles(boolean writeOutputFiles) {
		this.writeOutputFiles = writeOutputFiles;
		return this;
	}

	public RegulatoryReportingTestExtension withExclusionList(Multimap<String, String> exclusionList) {
		this.exclusionList = exclusionList;
		return this;
	}

	@Override
	public void beforeAll(ExtensionContext context) {
		ObjectMapper noExpectationWriteMapper = ObjectMapperGenerator.createWriterMapper(true);
		ObjectMapper writeMapper = ObjectMapperGenerator.createWriterMapper();
		this.descriptorWriter =  new DescriptorWriter(writeMapper);
		this.expectationWriter =  new ExpectationWriter(noExpectationWriteMapper, writeMapper);
		reportDescriptorLocation = Objects.requireNonNull(ClassPathUtils.getResource(regReportingRoot.resolve(DATA_FOLDER), RegulatoryReportingTestExtension.class.getClassLoader()));
		lookupDescriptorLocation =  Objects.requireNonNull(ClassPathUtils.getResource(regReportingRoot.resolve(LOOKUP_FOLDER), RegulatoryReportingTestExtension.class.getClassLoader()));
		if (isWriteOutputFiles()) {
			this.expectationWriter.writeMissingExpectationsFilesForAllDescriptors(SRC_DATA_FOLDER,
					descriptorWriter.readAllDescriptorFiles(SRC_DATA_FOLDER));
		}
		reportDataSetDefinitions = createJsonDescriptorLoader(getDataDescriptorNames()).load();
	}

	@Override
	public void afterAll(ExtensionContext context) throws Exception {
		if (writeOutputFiles) {
			// expectation file -> report name -> results
			Table<String, String, List<ExpectedResultField>> results = expectationWriter.groupByExpectationFileAndReportName(collectedExpectationResult, reportDataSetDefinitions);

			ObjectWriter objectWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
			for (String fileName : results.rowKeySet()) {
				Map<String, List<ExpectedResultField>> reportNameToExpectations = results.row(fileName);
				TreeMap<String, List<ExpectedResultField>> sortedReportNameToExpectations = new TreeMap<>(reportNameToExpectations);
				Path filePath = SRC_DATA_FOLDER.resolve(fileName);
				objectWriter.writeValue(filePath.toFile(), sortedReportNameToExpectations);
			}
		}
	}

	public List<String> getDataDescriptorNames() {
		return ClassPathUtils.findPathsFromClassPath(
						List.of(UrlUtils.toPortableString(regReportingRoot.resolve(DATA_FOLDER))),
						".*-descriptor\\.json",
						Optional.empty(),
						RegulatoryReportingTestExtension.class.getClassLoader()
				).stream().map(path -> path.getFileName().toString())
				.collect(Collectors.toList());
	}

	public JsonReportDataLoader createJsonDescriptorLoader(List<String> descriptorNames) {
		return new JsonReportDataLoader(getClass().getClassLoader(),
				expectationWriter.noExpectationWriteMapper,
				reportDescriptorLocation,
				descriptorNames);
	}

	public JsonReportDataLoader createJsonReportDataLoader(List<String> descriptorNames) {
		return new JsonReportDataLoader(getClass().getClassLoader(),
				expectationWriter.noExpectationWriteMapper,
				reportDescriptorLocation,
				descriptorNames,
				reportDescriptorLocation);
	}

	public JsonExpectedResultLoader createJsonExpectedResultLoader() {
		return new JsonExpectedResultLoader(getClass().getClassLoader(),
				expectationWriter.noExpectationWriteMapper,
				reportDescriptorLocation);
	}

	public JsonLookupDataLoader createJsonLookupDataLoader() {
		return new JsonLookupDataLoader(getClass().getClassLoader(),
				expectationWriter.noExpectationWriteMapper,
				lookupDescriptorLocation,
				getLookupDescriptorNames(),
				lookupDescriptorLocation);
	}

	public List<String> getLookupDescriptorNames() {
		return ClassPathUtils.findPathsFromClassPath(
						List.of(UrlUtils.toPortableString(regReportingRoot.resolve(LOOKUP_FOLDER))),
						".*-descriptor\\.json",
						Optional.empty(),
						RegulatoryReportingTestExtension.class.getClassLoader()
				).stream().map(path -> path.getFileName().toString())
				.collect(Collectors.toList());
	}

	public List<Arguments> loadTestArgs(ImmutableList<String> rosettaFolderPathNames) {
		List<RegReportIdentifier> regReportIdentifiers = reportUtil.loadRegReportIdentifier(rosettaFolderPathNames);
		List<Arguments> args = new ArrayList<>();
		for (RegReportIdentifier regReportIdentifier : regReportIdentifiers) {
			List<String> descrNames = getDataDescriptorNames();
			for (String dataDescriptorName : descrNames) {
				// Load descriptor files
				JsonReportDataLoader descriptorLoader = createJsonDescriptorLoader(List.of(dataDescriptorName));
				List<ReportDataSet> reportDataSets = descriptorLoader.load();
				for (ReportDataSet reportDataSet : reportDataSets) {
					// Enrich expected result
					JsonExpectedResultLoader jsonExpectedResultLoader = createJsonExpectedResultLoader();
					ReportDataSet reportDataSet1 = jsonExpectedResultLoader.loadInputFiles(new ReportIdentifierDataSet(regReportIdentifier, reportDataSet)).getDataSet();
					// Enrich input
					JsonReportDataLoader jsonReportDataLoader = createJsonReportDataLoader(Collections.emptyList());
					ReportDataSet reportDataSet2 = jsonReportDataLoader.loadInputFiles(reportDataSet1);
					// Build args
					args.add(Arguments.of(regReportIdentifier, reportDataSet2, regReportIdentifier.getName(), reportDataSet.getDataSetName()));
				}
			}
		}
		return args;
	}

	public void assertSameFields(RegReportIdentifier identifier,
								 List<ExpectedResultField> expectedReportFields,
								 List<ExpectedResultField> actualReportFields,
								 Multimap<String, String> exclusionList) {
		for (ExpectedResultField reportField : actualReportFields) {
			if (!exclusionList.containsEntry(identifier.getName(), reportField.getName())) {
				Optional<ExpectedResultField> expectedResultFieldOptional = expectedReportFields.stream()
						.filter(f -> f.getName().equals(reportField.getName()))
						.findFirst();
				if (isWriteOutputFiles()) {
					if (expectedResultFieldOptional.isPresent()) {
						ExpectedResultField expectedResultField = expectedResultFieldOptional.get();
						if (!reportField.equals(expectedResultField)) {
							LOGGER.warn("field {} not expected {}", reportField, expectedResultField);
						}
					} else {
						LOGGER.warn("field {} not found in the expected output for {}",
								reportField.getName(), identifier);
					}
				} else {
					assertThat(String.format("Field %s not found in the expected output for %s",
									reportField.getName(), identifier),
							expectedResultFieldOptional.isPresent(), equalTo(true));
					assertThat(reportField, equalTo(expectedResultFieldOptional.orElse(null)));
				}
			}
		}
	}

	public void assertSameNumberOfFields(List<ExpectedResultField> expectedReportFields, List<ExpectedResultField> actualReportFields) {
		String missingNames = String.join("", Sets.difference(
				actualReportFields.stream().map(ExpectedResultField::getName)
						.collect(Collectors.toSet()),
				expectedReportFields.stream().map(ExpectedResultField::getName).collect(Collectors.toSet())
		));
		if (isWriteOutputFiles()) {
			if (actualReportFields.size() != expectedReportFields.size()) {
				LOGGER.warn("missingNames {}", missingNames);
			}
		} else {
			assertThat(missingNames, actualReportFields.size(), equalTo(expectedReportFields.size()));
		}
	}

	public void assertRegReport(RegReportIdentifier identifier,
								List<RegReport> regReports,
								Multimap<String, String> exclusionList) {
		collectAllExpectationsIfWritingEnabled(identifier, regReports, exclusionList);

		for (RegReport regReport : regReports) {
			for (RegReportUseCase useCase : regReport.getUseCases()) {
				List<ExpectedResultField> actualReportFields = actualReportFields(useCase);
				List<ExpectedResultField> expectedResultFields = expectedResultFields(actualReportFields, exclusionList, identifier);
				assertRegReportUseCase(identifier, useCase, exclusionList, expectedResultFields);
			}
		}
	}

	private void collectAllExpectationsIfWritingEnabled(RegReportIdentifier identifier, List<RegReport> regReports, Multimap<String, String> exclusionList) {
		if (isWriteOutputFiles()) {
			for (RegReport regReport : regReports) {
				for (RegReportUseCase useCase : regReport.getUseCases()) {
					List<ExpectedResultField> actualReportFields = actualReportFields(useCase);
					List<ExpectedResultField> expectedResultFields = expectedResultFields(actualReportFields, exclusionList, identifier);
					String dataSetName = useCase.getDataSetName();
					String useCase1 = useCase.getUseCase();
					LOGGER.info("Collecting expectations for {} in {} for {}", useCase1, dataSetName, identifier.getName());
					collectedExpectationResult.add(new ExpectationResult(dataSetName, useCase1, identifier, expectedResultFields));
				}
			}
		}
	}

	private void assertRegReportUseCase(RegReportIdentifier identifier,
										RegReportUseCase useCase,
										Multimap<String, String> exclusionList, List<ExpectedResultField> reportFieldsWithoutExclusions) {
		List<ExpectedResultField> expectedReportFields = useCase.getExpectedResults().getExpectationsPerReport()
				.get(identifier.getName());


		if (expectedReportFields != null) {
			List<ExpectedResultField> expectedReportFieldsWithoutExclusions = expectedResultFields(expectedReportFields, exclusionList, identifier);
			LOGGER.info("Checking expectations found for {} in {} for {}", useCase.getUseCase(), useCase.getDataSetName(), identifier.getName());
			assertSameNumberOfFields(expectedReportFieldsWithoutExclusions, reportFieldsWithoutExclusions);
			assertSameFields(identifier, expectedReportFieldsWithoutExclusions, reportFieldsWithoutExclusions, exclusionList);
		} else {
			LOGGER.warn("No expectations found for {} in {} for {}", useCase.getUseCase(), useCase.getDataSetName(), identifier.getName());
		}
	}

	@NotNull
	private List<ExpectedResultField> expectedResultFields(List<ExpectedResultField> actualReportFields, Multimap<String, String> exclusionList, RegReportIdentifier identifier) {
		List<ExpectedResultField> reportFieldsWithoutExclusions = actualReportFields.stream()
				.filter(reportField -> !exclusionList.containsEntry(identifier.getName(), reportField.getName()))
				.collect(Collectors.toList());
		return reportFieldsWithoutExclusions;
	}

	@NotNull
	private List<ExpectedResultField> actualReportFields(RegReportUseCase useCase) {
		return useCase.getResults().stream()
				.map(f -> new ExpectedResultField(f.getName(), f.getValue()))
				.collect(Collectors.toList());
	}

	public ExpectationWriter getExpectationWriter() {
		return expectationWriter;
	}

	public URL getReportDescriptorLocation() {
		return reportDescriptorLocation;
	}

	public URL getLookupDescriptorLocation() {
		return lookupDescriptorLocation;
	}

	public boolean isWriteOutputFiles() {
		return writeOutputFiles;
	}

	public List<ExpectationResult> getCollectedExpectationResult() {
		return collectedExpectationResult;
	}

	public Multimap<String, String> getExclusionList() {
		return exclusionList;
	}
}
