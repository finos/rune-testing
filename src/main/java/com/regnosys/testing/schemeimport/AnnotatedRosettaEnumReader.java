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

import com.google.inject.Inject;
import com.regnosys.rosetta.rosetta.RosettaDocReference;
import com.regnosys.rosetta.rosetta.RosettaEnumeration;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaSegmentRef;
import com.regnosys.rosetta.transgest.ModelLoader;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class AnnotatedRosettaEnumReader {
    @Inject
	private ModelLoader modelLoader;

	public List<RosettaEnumeration> getAnnotatedEnum(List<RosettaModel> models, String body, String corpus) {
		List<RosettaEnumeration> allRosettaEnums = modelLoader.rosettaElements(models, RosettaEnumeration.class);

		return allRosettaEnums.stream()
			.filter(rosettaEnum -> enumIsAnnotatedWithBodyAndCorpusDocRef(rosettaEnum, body, corpus))
			.collect(Collectors.toList());
	}

	private boolean enumIsAnnotatedWithBodyAndCorpusDocRef(RosettaEnumeration rosettaEnumeration, String body, String corpus) {
		return rosettaEnumeration
			.getReferences()
				.stream().anyMatch(documentReference ->
				refIsAnnotatedWithBodyAndReference(documentReference, body, corpus));

	}

	private boolean refIsAnnotatedWithBodyAndReference(RosettaDocReference documentReference, String body, String corpus) {
		return documentReference.getDocReference() != null
				&& documentReference.getDocReference().getBody() != null
				&& body.equals(documentReference.getDocReference().getBody().getName())
				&& documentReference.getDocReference().getCorpusList().stream()
				.filter(Objects::nonNull)
				.anyMatch(x -> corpus.equals(x.getName()));
	}

	public Optional<String> getSchemaLocationForEnum(RosettaEnumeration rosettaEnumeration) {
	 	return rosettaEnumeration
			.getReferences()
			.stream()
			.flatMap(ref -> ref.getDocReference().getSegments().stream())
			.filter(s -> s.getSegment().getName().equals("schemeLocation"))
			.map(RosettaSegmentRef::getSegmentRef)
			.findAny();
	}
}
