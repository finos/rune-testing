package com.regnosys.testing.reports;

public class FileNameProcessor {
	public static String removeFileExtension(String fileName) {
		return fileName.replaceAll("\\.(json|xml)$", "");
	}

	public static String removeFilePrefix(String file) {
		return file.replaceAll(".*/(.*?\\.(json|xml)$)", "$1");
	}

	public static String sanitizeFileName(String file) {
		return file.replaceAll("[^A-Za-z0-9-._]+", "");
	}
}
