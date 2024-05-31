package com.regnosys.testing;

/*-
 * #%L
 * Rosetta Testing
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

import com.regnosys.rosetta.generator.RosettaGenerator;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.tests.compiler.InMemoryJavacCompiler;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.generator.GeneratorContext;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.testing.validation.ValidationTestHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.xbase.testing.RegisteringFileSystemAccess;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

// TODO: this is duplicated in the DSL! See `CodeGeneratorTestHelper` and `ModelHelper` in DSL test project.
public class ModelHelper {
    private static final String commonTestTypes =
              "namespace \"com.rosetta.test.model\"\n"
            + "version \"test\"\n"
            + "\n"
            + "metaType scheme string\n";

	@Inject
	private RosettaGenerator rosettaGenerator;

	@Inject
	private ParseHelper<RosettaModel> parseHelper;

	@Inject
	private ValidationTestHelper validationTestHelper;

	public GeneratedCode generateCode(CharSequence... models) throws Exception {
		return generateCode(parseRosettaWithNoErrors(models).stream().map(EObject::eResource).collect(Collectors.toList()));
	}

	public CompiledCode compileCode(GeneratedCode generatedCode) {
		return inMemoryCompileToClasses(generatedCode, this.getClass().getClassLoader());
	}

	public CompiledCode generateAndCompileJava(CharSequence... models) throws Exception {
		return compileCode(generateCode(models));
	}

	private  CompiledCode inMemoryCompileToClasses(GeneratedCode generatedCode, ClassLoader parentClassLoader) {
        InMemoryJavacCompiler inMemoryCompiler = InMemoryJavacCompiler
                .newInstance()
                .useParentClassLoader(parentClassLoader)
                .useOptions(
                        "--release", "8",
                        "-Xlint:all", "-Xdiags:verbose");

        generatedCode.getGenerated().forEach(inMemoryCompiler::addSource);

        return new CompiledCode(inMemoryCompiler.compileAll().values());
	}

	public GeneratedCode generateCode(List<Resource> resources) {
		var fsa = new RegisteringFileSystemAccess();
		var ctx = new GeneratorContext() {
			@Override
			public CancelIndicator getCancelIndicator() {
				return CancelIndicator.NullImpl;
			}
		};

        ResourceSet resourceSet = resources.get(0).getResourceSet();
        try {
            rosettaGenerator.beforeAllGenerate(resourceSet, fsa, ctx);
            for (Resource eResource : resources) {
                try {
                    rosettaGenerator.beforeGenerate(eResource, fsa, ctx);
                    rosettaGenerator.doGenerate(eResource, fsa, ctx);
                } finally {
                    rosettaGenerator.afterGenerate(eResource, fsa, ctx);
                }
            }
        } finally {
            rosettaGenerator.afterAllGenerate(resourceSet, fsa, ctx);
        }

		var generatedCode = new HashMap<String, String>();

		fsa.getGeneratedFiles()
			.forEach(it -> {
				if (it.getJavaClassName() != null) {
					generatedCode.put(it.getJavaClassName(), it.getContents().toString());
				}
			});

		return new GeneratedCode(generatedCode);
	}

	public List<RosettaModel> parseRosettaWithNoErrors(CharSequence... models) throws Exception {
		var parsed = parseRosetta(models);
		for (RosettaModel rosettaModel : parsed) {
			validationTestHelper.assertNoErrors(rosettaModel);
		}
		return parsed;
	}

	private List<RosettaModel> parseRosetta(CharSequence... models) throws Exception {
		var resourceSet = testResourceSet();
		var rosettaModels = new ArrayList<RosettaModel>();
		for (CharSequence model : models) {
			rosettaModels.add(parseHelper.parse(model, resourceSet));
		}
		return rosettaModels;
	}

	private ResourceSet testResourceSet() {
        try {
            ResourceSet resourceSet = parseHelper.parse(ModelHelper.commonTestTypes).eResource().getResourceSet();
            resourceSet.getResource(URI.createURI("classpath:/model/basictypes.rosetta"), true);
            resourceSet.getResource(URI.createURI("classpath:/model/annotations.rosetta"), true);
            return resourceSet;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
	}
}
