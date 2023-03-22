package com.regnosys.testing;

import com.google.common.collect.ImmutableList;
import com.regnosys.rosetta.common.util.Pair;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RosettaFileNameValidator {

    private static final List<String> VALID_SUFFIX = ImmutableList.of("func", "rule", "enum", "type", "synonym", "desc");
    private final String modelShortName;
    private final Path pathToRosettaFiles;
    private final Optional<Path> parentModelExclusionFile;

    public RosettaFileNameValidator(String modelShortName, Path pathToRosettaFiles, @Nullable Path parentModelExclusionFile) {
        this.modelShortName = modelShortName;
        this.pathToRosettaFiles = pathToRosettaFiles;
        this.parentModelExclusionFile = Optional.ofNullable(parentModelExclusionFile);
    }

    public ValidationReport validateFileNamesMatchNamespace() throws IOException {
        Set<String> parentModelFileNames = getParentModelFileNames();

        List<String> errors = Files.walk(pathToRosettaFiles)
                .filter(x -> x.getFileName().toString().endsWith(".rosetta"))
                .filter(x -> !parentModelFileNames.contains(x.getFileName().toString()))
                .map(this::extractNamespace)
                .map(rosettaFileToNamespace ->
                        ensureFileNameSuffix(modelShortName, rosettaFileToNamespace.left(), rosettaFileToNamespace.right()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        ValidationReport validationReport = new ValidationReport(true, errors);

        if (!errors.isEmpty()) {
            validationReport = new ValidationReport(false, errors);
        }
        return validationReport;
    }

    private Set<String> getParentModelFileNames() throws IOException {
        if (parentModelExclusionFile.isPresent()) {
            return Files.walk(parentModelExclusionFile.get()).map(Path::getFileName).map(Path::toString)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    private List<String> ensureFileNameSuffix(String modelShortName, String rosettaFileName, String rosettaNamespace) {
        List<String> validationResults = new ArrayList<>();

        String name = rosettaFileName.substring(0, rosettaFileName.indexOf(".rosetta"));
        String[] parts = name.split("-");
        if (parts.length == 1) {
            validationResults.add("\n No suffix for file '" + rosettaFileName + "' with namespace '" + rosettaNamespace + "'. Should be one of " + VALID_SUFFIX + ". ");
            //return validationResults;
        }
        else{
            String suffix = parts[parts.length - 1];
            if (!VALID_SUFFIX.contains(suffix)) {
                validationResults.add("\n Suffix for file '" + rosettaFileName + "' with namespace '" + rosettaNamespace + "'. Should be one of " + VALID_SUFFIX + ". ");
                //return validationResults;
            }
        }


        String fileWithoutSuffix = modelShortName + "." + String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 1));

        if (null == rosettaNamespace) {
            validationResults.add("\n File name '" + rosettaFileName + "' is not in line with namespace '" + rosettaNamespace + "'. Namespace should be '" + fileWithoutSuffix + "'. ");
            //return validationResults;
        } else {

            if (!modelShortName.equals(rosettaNamespace.split("\\.")[0])) {
                validationResults.add("\n File '" + rosettaFileName + "' with namespace '" + rosettaNamespace + "'. Namespace should start with model name '" + modelShortName + "'. ");
                //return validationResults;
            }

            if (!rosettaNamespace.equals(fileWithoutSuffix)) {
                validationResults.add("\n File name '" + rosettaFileName + "' is not in line with namespace '" + rosettaNamespace + "'. Namespace should be '" + fileWithoutSuffix + "'. ");
                //return validationResults;
            }

        }
        return validationResults;
    }

    private Pair<String, String> extractNamespace(Path rosettaFile) {
        try {
            return Files.readAllLines(rosettaFile).stream()
                    .filter(line -> line.contains("namespace"))
                    .map(line -> extractNamespaceFromLine(rosettaFile, line))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find namespace for " + rosettaFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Pair<String, String> extractNamespaceFromLine(Path rosettaFile, String line) {
        Pattern pattern = Pattern.compile("^namespace ([a-zA-Z0-9_\\.]*)");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find() && matcher.groupCount() == 1) {
            return Pair.of(rosettaFile.getFileName().toString(), matcher.group(1));
        }
        return Pair.of(rosettaFile.getFileName().toString(), null);
    }
}
