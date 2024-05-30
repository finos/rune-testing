package com.regnosys.testing;

/*-
 * #%L
 * Rune Testing
 * %%
 * Copyright (C) 2022 - 2024 REGnosys
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
