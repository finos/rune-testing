package com.regnosys.testing.reports;

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
