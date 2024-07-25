package com.regnosys.testing.pipeline;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Iterables;
import com.rosetta.model.lib.annotations.RosettaReport;
import com.rosetta.model.lib.functions.RosettaFunction;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class FunctionNameHelper {

    public String getInputType(Class<? extends RosettaFunction> function) {
        Method functionMethod = getFuncMethod(function);
        return functionMethod.getParameterTypes()[0].getName();
    }

    public String getOutputType(Class<? extends RosettaFunction> function) {
        Method functionMethod = getFuncMethod(function);
        return functionMethod.getReturnType().getName();
    }

    public Method getFuncMethod(Class<? extends RosettaFunction> function) {
        List<Method> evaluateMethods = Arrays.stream(function.getMethods())
                .filter(x -> x.getName().equals("evaluate"))
                .collect(Collectors.toList());
        return Iterables.getLast(evaluateMethods);
    }

    public String getName(Class<? extends RosettaFunction> function) {
        return Optional.ofNullable(function.getAnnotation(com.rosetta.model.lib.annotations.RosettaReport.class))
                .map(a -> String.format("%s / %s", a.body(), String.join(" ", a.corpusList())))
                .orElse(readableFunctionName(function));
    }

    private String readableFunctionName(Class<? extends RosettaFunction> function) {
        String readableId = readableId(function);

        return Arrays.stream(readableId.split("-"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                .collect(Collectors.joining(" "));
    }

    protected String readableId(Class<? extends RosettaFunction> function) {
        String simpleName = Optional.ofNullable(function.getAnnotation(RosettaReport.class))
                .map(a -> String.format("%s-%s", a.body(), String.join("-", a.corpusList())))
                .orElse(function.getSimpleName());

        String sanitise = simpleName
                .replace("Report", "")
                .replace("Function", "")
                .replace("Project", "")
                .replace("-", ".")
                .replace("_", ".");

        String functionName = lowercaseConsecutiveUppercase(sanitise)
                .replace(".", "");

        return CaseFormat.UPPER_CAMEL
                .converterTo(CaseFormat.LOWER_HYPHEN)
                .convert(functionName);
    }

    private String lowercaseConsecutiveUppercase(String input) {
        StringBuilder result = new StringBuilder();
        boolean inUppercaseSequence = false;
        for (int i = 0; i < input.length(); i++) {
            char currentChar = input.charAt(i);
            char newChar = currentChar;
            boolean isLastChar = i == input.length() - 1;
            if (Character.isUpperCase(currentChar)) {
                if (!inUppercaseSequence) {
                    // Append the first uppercase character
                    inUppercaseSequence = true;
                } else if (isLastChar || Character.isUpperCase(input.charAt(i + 1)) || input.charAt(i + 1) == '.') {
                    newChar = Character.toLowerCase(currentChar);
                    // Lowercase the middle characters
                } else {
                    // Append the last uppercase character
                    inUppercaseSequence = false;
                }
            } else {
                // Append lowercase or non-letter characters
                inUppercaseSequence = false;
            }
            result.append(newChar);
        }
        return result.toString();
    }

}
