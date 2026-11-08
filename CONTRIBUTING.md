# Contributing to Veyra

Bug reports, shader pack compatibility fixes, and focused pull requests are welcome. For user setup, see the [README](README.md).

## Build and test

Use **JDK 21** and the included Gradle wrapper. You do not need a separate Gradle installation.

```sh
./gradlew build validateShaders
```

On Windows:

```powershell
.\gradlew.bat build validateShaders
```

`build` runs the Java regression tests and produces remapped mod and sources JARs in `build/libs/`. `validateShaders` runs CPU shaderc checks. Development dependencies are declared in Gradle, including runtime libraries normally nested inside the published VulkanMod JAR.

For changes to the Python validation tools, also run:

```sh
python -m unittest discover -s scripts/tests -v
```

## Shader examples and graphics validation

The [examples](examples/) are small, original shader packs that exercise individual rendering features. Package them with:

```sh
./gradlew packageExamplePacks
```

Extract `build/distributions/veyra-example-packs-*.zip` into your instance's `shaderpacks/` folder. Start with `grayscale` or `animated` for post-processing.

Rendering changes need appropriate graphics checks as well as unit tests. Follow [VALIDATION.md](VALIDATION.md) and report the GPU, driver, pack version, profile, and checks you actually ran. A successful source audit or synthetic fixture alone does not prove full-pack visual compatibility.

## Pull requests

- Keep the change focused and follow the existing code structure.
- Explain the problem, resulting behavior, and validation performed.
- Include a regression test for non-trivial fixes where practical; use a focused shader fixture for rendering behavior.
- Update [COMPATIBILITY.md](COMPATIBILITY.md) when the supported shader contract changes.
- Do not commit local game instances, logs, downloaded third-party packs, or generated build and validation outputs.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the rendering design and the [shader guide](docs/SHADER_GUIDE.md) for program inputs and examples.

## Release maintenance

Update [RELEASE_NOTES.md](RELEASE_NOTES.md) to the verified release scope and document remaining limitations before publishing. The Release workflow accepts a branch named `release/<mod_version>` or a tag named `v<mod_version>`, matching `gradle.properties`. It builds and tests that commit and publishes the mod, sources, example packs, and SHA-256 checksums.

## Agent handoff

[WORK_SUMMARY.md](WORK_SUMMARY.md) is the retained agent work log. Keep historical evidence there separate from user-facing installation and release documentation. [GOAL.md](GOAL.md) records the historical 1.0.0 task context.
