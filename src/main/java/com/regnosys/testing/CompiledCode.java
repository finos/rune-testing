package com.regnosys.testing;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class CompiledCode {
	private final Collection<Class<?>> compiledCode;

	public CompiledCode(Collection<Class<?>> compiledCode) {
		this.compiledCode = compiledCode;
	}

	@SuppressWarnings("unchecked")
	public <T> Class<T> loadClass(String className) {
		return (Class<T>) compiledCode.stream().filter(x -> x.getName().equals(className)).findAny()
			.orElseThrow(() -> new IllegalArgumentException(className + " was not compiled. Available classes are " + classNames()));
	}

	public Set<String> classNames() {
		return compiledCode.stream().map(Class::getName).collect(Collectors.toSet());
	}

}
