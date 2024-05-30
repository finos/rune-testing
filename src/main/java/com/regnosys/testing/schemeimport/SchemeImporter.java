package com.regnosys.testing.schemeimport;

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

import com.regnosys.rosetta.rosetta.RosettaEnumValue;
import com.regnosys.rosetta.rosetta.RosettaEnumeration;
import com.regnosys.rosetta.rosetta.RosettaModel;
import org.eclipse.emf.ecore.resource.Resource;

import javax.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SchemeImporter {
    @Inject
	private AnnotatedRosettaEnumReader enumReader;
    @Inject
	private RosettaResourceWriter rosettaResourceWriter;

	public Map<String, String> generateRosettaEnums(List<RosettaEnumeration> enums) {
		Map<Resource, List<RosettaEnumeration>> enumsGroupedByRosettaResource = enums.stream()
			.collect(Collectors.groupingBy(x -> x.eContainer().eResource()));
		return rosettaResourceWriter.generateRosettaFiles(enumsGroupedByRosettaResource.keySet());
	}

	public List<RosettaEnumValue> getEnumValuesFromCodingScheme(RosettaEnumeration annotatedEnum, SchemeEnumReader schemeEnumReader){
		return enumReader.getSchemaLocationForEnum(annotatedEnum)
				.map(schemaLocationForEnum -> {
					try {
						return schemeEnumReader.generateEnumFromScheme(new URL(schemaLocationForEnum));
					} catch (MalformedURLException e) {
						throw new RuntimeException(e);
					}
				})
				.orElse(List.of());
	}

	public List<RosettaEnumeration> getRosettaEnumsFromModel(List<RosettaModel> models, String body, String corpus) {
		return  enumReader.getAnnotatedEnum(models, body, corpus);
	}




}
