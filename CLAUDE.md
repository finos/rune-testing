# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Rune Testing (Maven artifact `org.finos.rune-testing:rune-testing`) is a Java library providing shared
test infrastructure for projects that use the [Rune DSL](https://github.com/finos/rune-dsl)
(`org.finos.rune`). It is consumed as a dependency by generated Rune models and by
[Rune Code Generators](https://github.com/REGnosys/rosetta-code-generators) — it is not an application,
it's a toolkit of JUnit extensions, Guice/Xtext wiring, and test-pack/pipeline utilities that downstream
Rune model repos use to test their generated code.

The canonical upstream is `finos/rune-testing` (matches the `<scm>` block in `pom.xml`), remote name
`finos` in this checkout — PRs should be opened against that org/repo, not `REGnosys/rune-testing`
(remote `origin`), which is a fork/mirror.

## Build & test commands

Requires JDK 21 (`java.enforced.version` is `[21,22)`) and Maven.

```sh
mvn clean install          # full build, install to local repo (what downstream repos need)
mvn clean package          # build + test without installing
mvn test                   # run tests only
mvn -Dtest=ClassName test  # run a single test class
mvn -Dtest=ClassName#methodName test   # run a single test method
```

Checkstyle runs automatically during the build (`process-test-sources` phase, config in `checkstyle.xml`)
and fails the build on violation — it forbids `com.google.inject.Inject`/`Named`/`Provider`/`Singleton`
and the `javax.inject` package; use `jakarta.inject.Inject` instead.

`src/generated/java` (JAXB classes for FpML and ISO 4217 currency coding schemes) is generated at
`generate-sources` time by the `cxf-xjc-plugin` from XSDs under `src/main/resources/coding-schemes`, and
wiped by `mvn clean`. Never hand-edit files there.

License headers in `src/main/java` and `src/test` are auto-inserted/updated by `license-maven-plugin` at
`process-sources` time — don't hand-write the header block, let the build add it (or copy the exact block
from a neighboring file if writing offline).

CI (`.github/workflows/check-pr.yml`) runs `mvn -B clean package` on both Ubuntu and Windows for every PR —
keep file/path handling platform-portable (see `PipelineTestPackWriterTest` for portable-path patterns).

## Architecture

The library has two halves: (1) a Guice/Xtext harness for driving the Rune DSL compiler/interpreter in
tests, and (2) a pipeline/test-pack framework built on top of it for testing generated model *transform
functions* (translations between models, and reports) against recorded sample input/output pairs.

### Xtext/Guice wiring (`com.regnosys.testing` root)

- `RosettaTestingModule` — Guice module extending the DSL's `RosettaRuntimeModule`.
- `RosettaTestingInjectorProvider` — extends the DSL's `RosettaInjectorProvider`; creates the Xtext
  injector used by tests that need to parse/validate `.rosetta` files directly (via
  `RosettaStandaloneSetup`).
- `ModelHelper` — builds small in-memory Rune model source for tests. Note: intentionally duplicated from
  `CodeGeneratorTestHelper`/`ModelHelper` in the rune-dsl test project (see TODO in the file) — changes
  there may need mirroring here.
- `CompiledCode` / `GeneratedCode` — wrap the classes/sources produced when compiling a Rune model on the
  fly during a test.

### Pipeline framework (`com.regnosys.testing.pipeline`)

Models a Rune "pipeline" (a config-driven chain of transform functions between model types, e.g.
report/translate steps) and runs it against sample data:

- `PipelineTree` / `PipelineTreeBuilder` / `PipelineNode` / `PipelineTreeConfig` — build a tree of
  transform steps from pipeline config.
- `PipelineModelBuilder` — discovers Rune functions/reports (via annotations) and builds `PipelineModel`
  objects describing each transform (input/output types, serialisation formats).
- `PipelineFunctionRunner` / `PipelineFunctionRunnerImpl` / `PipelineFunctionRunnerProvider(Impl)` —
  execute a single transform function against a sample input file: deserialise input, resolve references,
  run the function, post-process, serialise output, run type + XSD validation, and report path-count
  assertions (`PipelineFunctionResult`).
- `PipelineConfigWriter` / `PipelineModelWriter` / `PipelineTestPackWriter` / `PipelineTestPackFilter` /
  `PipelineFilter` — write the pipeline/test-pack JSON config files consumed by generators and by
  `TransformTestExtension`.

### Transform test extension (`com.regnosys.testing.transform`, `com.regnosys.testing.testpack`)

`TransformTestExtension<T>` is the main entry point downstream model repos use directly: a JUnit 5
`BeforeAllCallback`/`AfterAllCallback` extension that, given a function class and a pipeline config path,
runs that function against every sample in its test-pack and asserts serialised output + assertions match
recorded expectations (`getArguments()` feeds a `@ParameterizedTest`). `TestPackModelHelper(Impl)` loads
`.rosetta` models/reports/functions for test-pack generation tooling.

Expectation regeneration is controlled by env vars (`TestingExpectationUtil`), not code changes:
- `WRITE_EXPECTATIONS=true` — capture actual output for later use.
- `CREATE_EXPECTATION_FILES=true` — create new expectation files that don't exist yet.
- `TEST_WRITE_BASE_PATH` — base path to write regenerated expectation files to.

### Default JSON serialisation (`com.regnosys.testing.serialisation`)

`DefaultModelSerialisation.resolve(classLoader)` picks the correct `ObjectMapper`/`ObjectWriter` for the
model under test — the new `RuneJsonObjectMapper` if the model's `rune-config.yml`/`rosetta-config.yml`
sets `defaultSerialisationFormat: RUNE_JSON`, otherwise the legacy `RosettaObjectMapper`. This deliberately
mirrors `JarModelInstance.getDefaultJsonMapper()` on the rosetta-products side so a model's JUnit tests
serialise identically to how the Rosetta runtime would. Any failure to read/parse the config falls back
silently to the legacy mapper (models that don't opt in keep prior behaviour). Note `TransformTestExtension`
uses this resolved mapper only for *domain data* — the pipeline/test-pack config JSON itself is always read
with the legacy `RosettaObjectMapper`, independent of the model's configured default.

### Other areas

- `com.regnosys.testing.schemeimport` — imports FpML and ISO 4217 coding schemes (via the JAXB classes in
  `src/generated/java`) into Rune enum resource files (`SchemeImporter`, `RosettaResourceWriter`,
  `*SchemeEnumReader`).
- `com.regnosys.testing.reports` — helpers for testing Rune report functions (`ReportTypeSummariser`,
  `FilterProvider`, `ObjectMapperGenerator`; `ReportUtil` is marked `@Deprecated`).
- `com.regnosys.testing.validation` — `ValidationSummariser` for summarising `RosettaTypeValidator` output.
- `com.regnosys.testing.performance` — `PerformanceTest`/`PerformanceTestHarness` framework for
  load/perf-testing model functions, plus an `http` variant for HTTP-based targets.
- `RosettaFileNameValidator` — enforces `.rosetta` file naming conventions (suffixes: `func`, `rule`,
  `enum`, `type`, `synonym` (legacy), `desc`).
- `util.UnusedModelElementFinder` — finds Rune model elements with no references, for cleanup tooling.

## Dependency notes

This library pins `org.finos.rune:rune-lang`/`rune-testing` (`rune.dsl.version`) and
`org.finos.rune-common:rune-common` (`rune.common.version`) in `pom.xml` — most "why does behaviour differ"
questions trace back to which DSL/common version is in play, so check those properties first when
debugging cross-version issues.
