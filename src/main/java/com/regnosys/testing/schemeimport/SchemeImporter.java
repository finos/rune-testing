package com.regnosys.testing.schemeimport;

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