package com.regnosys.testing.testpack;

import java.util.Objects;

public class NameAndInputType {

    private final String name;
    private final String inputType;

    public NameAndInputType(String name, String inputType) {
        this.name = name;
        this.inputType = inputType;
    }

    public String getName() {
        return name;
    }

    public String getInputType() {
        return inputType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NameAndInputType that = (NameAndInputType) o;
        return Objects.equals(name, that.name) && Objects.equals(inputType, that.inputType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, inputType);
    }
}
