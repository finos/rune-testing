package com.regnosys.testing;

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
