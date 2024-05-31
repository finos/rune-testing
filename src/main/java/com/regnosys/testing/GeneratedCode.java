package com.regnosys.testing;

/*-
 * ===============
 * Rosetta Testing
 * ===============
 * Copyright (C) 2022 - 2024 REGnosys
 * ===============
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
 * ===============
 */

import org.eclipse.xtext.xbase.testing.JavaSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GeneratedCode {

	private final Map<String, String> generated;

	public GeneratedCode(Map<String, String> generated) {
		this.generated = generated;
	}

	public Map<String, String> getGenerated() {
		return generated;
	}

	public Set<String> getGeneratedClassNames() {
		return generated.keySet();
	}

	public List<JavaSource> getJavaSource() {
		return generated.entrySet().stream()
			.map(e -> new JavaSource(toJavaFile(e.getKey()), e.getValue())).collect(Collectors.toList());

	}

	public void writeClasses(String directory) throws IOException {
		for (Map.Entry<String, String> entry : generated.entrySet()) {
			var className = entry.getKey();
			var path = Paths.get("target/" + directory + "/java", toJavaFile(className));
			Files.createDirectories(path.getParent());
			Files.write(path, entry.getValue().getBytes());
		}
	}

	private String toJavaFile(String className) {
		return className.replace(".", File.separator) + ".java";
	}

}
