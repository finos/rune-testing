package com.regnosys.testing;

import com.google.inject.Provider;
import com.regnosys.rosetta.generator.RosettaGenerator;
import com.regnosys.rosetta.rosetta.RosettaModel;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.xtext.generator.GeneratorContext;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.testing.util.ResourceHelper;
import org.eclipse.xtext.testing.validation.ValidationTestHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.JavaVersion;
import org.eclipse.xtext.xbase.testing.InMemoryJavaCompiler;
import org.eclipse.xtext.xbase.testing.JavaSource;
import org.eclipse.xtext.xbase.testing.RegisteringFileSystemAccess;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
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
	private Provider<XtextResourceSet> resourceSetProvider;

	@Inject
	private ResourceHelper resourceHelper;

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

	private  CompiledCode inMemoryCompileToClasses(GeneratedCode generatedCode, ClassLoader scope) {
		InMemoryJavaCompiler inMemoryCompiler = new InMemoryJavaCompiler(scope, JavaVersion.JAVA8);

		List<JavaSource> javaSources = generatedCode.getJavaSource();

		InMemoryJavaCompiler.Result result = inMemoryCompiler.compile(javaSources.toArray(JavaSource[]::new));

		try {
			if (result.getCompilationProblems().stream().anyMatch(IProblem::isError)) {
				throw new IllegalArgumentException("Java code compiled with errors: " + result
					.getCompilationProblems());
			}
			var classLoader = result.getClassLoader();

			var classes = new ArrayList<Class<?>>();
			for (String source : generatedCode.getGeneratedClassNames()) {
				classes.add(classLoader.loadClass(source));
			}
			return new CompiledCode(classes);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e.getMessage() + ", " + generatedCode.getGeneratedClassNames() + ", " + result.getCompilationProblems()
				.stream().map(Objects::toString).collect(Collectors.joining("\n")), e);
		}
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
