package com.regnosys.testing.transform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.transform.TestPackModel.SampleModel;
import com.regnosys.testing.reports.FileNameProcessor;
import com.regnosys.testing.reports.FilterProvider;
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

public class TestPackConfigWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger (TestPackConfigWriter.class);

    private final ObjectMapper writeMapper;
    private final RegReportPaths paths;

    public TestPackConfigWriter(ObjectMapper writeMapper) {
        this (writeMapper, RegReportPaths.getDefault ( ));
    }

    public TestPackConfigWriter(ObjectMapper writeMapper, RegReportPaths paths) {
        this.writeMapper = writeMapper;
        this.paths = paths;
    }

    public void writeConfigFile(Path resourcesPath, Path configPath, TestPackModel testPackModel) {
        Path path = generateTestPackModelFilePath (configPath, testPackModel.getId ( ));
        SimpleFilterProvider filterProvider = FilterProvider.getExpectedTypeFilter ( );

        try {
            TestPackModel filteredTestPackModel = sortAndRemoveUningestedFiles (resourcesPath, testPackModel);
            Path fullPath = resourcesPath.resolve (path);
            Files.createDirectories (fullPath.getParent ( ));
            try (BufferedWriter writer = Files.newBufferedWriter (fullPath)) {
                writer.write (
                        writeMapper.writer (filterProvider)
                                .withDefaultPrettyPrinter ( )
                                .writeValueAsString (filteredTestPackModel)
                );
                LOGGER.info ("Writing descriptor file: {}", path);
            }
        } catch (IOException e) {
            throw new RuntimeException (e);
        }
    }

    private TestPackModel sortAndRemoveUningestedFiles(Path resourcesPath, TestPackModel testPackModel) {
        List<SampleModel> sampleModels = testPackModel.getSamples ( )
                .stream ( )
                .filter (sampleModel -> sampleModelInputFileExists (resourcesPath, sampleModel))
                .sorted (Comparator.comparing (SampleModel::getId))
                .collect (Collectors.toList ( ));

        return new TestPackModel (testPackModel.getId ( ),
                testPackModel.getPipelineId ( ),
                testPackModel.getName ( ),
                sampleModels);
    }

    private boolean sampleModelInputFileExists(Path resourcesPath, SampleModel model) {
        String input = model.getInputPath ( ).toString ( );
        return Files.exists (resourcesPath.resolve (input));
    }

    private List<TestPackModel> readTestPackModelFile(Path file) {
        try {
            return writeMapper.readValue (file.toFile ( ), new TypeReference<> ( ) {
            });
        } catch (IOException e) {
            throw new RuntimeException (e);
        }
    }

    public Map<Path, List<TestPackModel>> readAllDescriptorFiles(Path testPackModelParentDirectory) {
        try {
            return Files.walk (testPackModelParentDirectory)
                    .filter (p -> p.getFileName ( ).toString ( ).startsWith ("test-pack-"))
                    .collect (Collectors.toMap (
                            path -> path,
                            this::readTestPackModelFile
                    ));
        } catch (IOException e) {
            throw new UncheckedIOException (e);
        }
    }

    private Path generateTestPackModelFilePath(Path outFolder, String Filename) {

        return outFolder.resolve (FileNameProcessor.sanitizeFileName (Filename + ".json"));
    }
}
