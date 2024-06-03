package com.regnosys.testing.schemeimport;

/*-
 * ===============
 * Rune Testing
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

import org.eclipse.emf.ecore.resource.Resource;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class RosettaResourceWriter {
	public Map<String, String> generateRosettaFiles(Collection<Resource> resources) {
		Map<String, String> results = new HashMap<>();
		for (Resource resource : resources) {
			try {
				String out = removeDescriptionWhitespace(removeEscapedQuotes(rewriteProjectVersion(writeOut(resource))));
				String fileName = resource.getURI().lastSegment();
				results.put(fileName, out);
			} catch (URISyntaxException | IOException | ExecutionException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		return results;
	}

	// This is required because the branch gets generated as the version, so you would get 'version "0.0.0.master"'
	public static String rewriteProjectVersion(String out) {
		return out.replaceAll("version \".*?\"", "version \"\\${project.version}\"");
	}

	private static String removeEscapedQuotes(String out) {
		return out.replace("\\'", "'");
	}

	private static String removeDescriptionWhitespace(String out) {
		return out.replace("< \"", "<\"").replace("\" >", "\">");
	}

	private String writeOut(Resource eResource)
		throws URISyntaxException, IOException, ExecutionException, InterruptedException {

		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		BufferedOutputStream out = new BufferedOutputStream(byteArrayOutputStream);
		eResource.save(out, null);
		return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(byteArrayOutputStream.toByteArray())).toString();
	}

}
