# Contributing to Veyra

Thanks for your interest in Veyra. This document explains how to build the project, where documentation belongs, and how releases are made.

## Development setup

Requirements: JDK 21. The Gradle wrapper is included.

```sh
./gradlew build validateShaders packageExamplePacks
python3 scripts/verify_jar.py
```

- `build` compiles and remaps the mod and runs the JUnit tests.
- `validateShaders` compiles the example packs with shaderc.
- `runClient` starts a development client with VulkanMod.

Pull requests run the [Build](.github/workflows/build.yml) workflow. Changes to the renderer, shader loader, or graphics fixtures also run the [Graphics smoke](.github/workflows/graphics-smoke.yml) and [Shaderpack audit](.github/workflows/shaderpack-audit.yml) workflows.

## Pull requests

- Keep each pull request focused on one change.
- Add or update a regression test for every bug fix and new shader input.
- If a change is visible to shader pack authors, add an example under `examples/`, or extend an existing one, and list it in [docs/examples.md](docs/examples.md). If the feature can be checked by pixel readback, add it to the graphics smoke matrix.
- Update the documentation in the same pull request (see below).

## Documentation

Each document has one purpose. Do not repeat the same information across files.

| File | Purpose | Update when |
| --- | --- | --- |
| [README.md](README.md) | What Veyra is, installation, usage | Requirements, installation, or the latest release change |
| [CHANGELOG.md](CHANGELOG.md) | Release history | Every user-visible change, under `[Unreleased]` |
| [docs/shader-support.md](docs/shader-support.md) | Current supported programs, inputs, and uniforms | A shader feature is added, changed, or removed |
| [docs/compatibility.md](docs/compatibility.md) | Current third-party pack results | The pinned audit results change |
| [docs/architecture.md](docs/architecture.md) | Internal design and known gaps | The rendering design or gap list changes |
| [docs/validation.md](docs/validation.md) | Test strategy and current results | Checks are added or their results change |
| [docs/examples.md](docs/examples.md) | Example pack catalog | An example is added or changed |
| [RELEASE_NOTES.md](RELEASE_NOTES.md) | Body of the next GitHub release | When preparing a release |

Guidelines:

- Write all documentation, commit messages, and pull request descriptions in English.
- Reference documents describe the **current** state. History belongs only in the changelog and in git.
- Do not record CI run IDs, commit hashes, JAR sizes, or checksums in the repository. GitHub Actions and the release assets already keep them.
- Do not mention version numbers in reference documents ("beta.8 adds…"). Describe what the feature does.

### Changelog entries

Follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/):

- Add entries under `## [Unreleased]`, grouped as `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, or `Security`.
- Write one short line per change, from the user's point of view, in past or descriptive tense.
- Leave out test counts, audit numbers, and CI results; they belong in the release verification, not the history.

## Releases

Releases are built and published by the [Release](.github/workflows/release.yml) workflow. A release needs **one** pull request:

1. Set `mod_version` in `gradle.properties` (for example `0.2.0-beta.24+mc1.21.11`).
2. In `CHANGELOG.md`, rename `[Unreleased]` to the new version with today's date, add a new empty `[Unreleased]` section, and update the comparison links at the bottom.
3. Replace `RELEASE_NOTES.md` with the new version's changelog entries, plus installation and known limits.
4. Update the latest release link in `README.md` and, if results changed, `docs/compatibility.md` and `docs/validation.md`.
5. After merging, push the tag `v<mod_version>` on the merge commit. The workflow checks that the tag matches `mod_version`, reruns all checks, and publishes the JAR, sources, example packs, and `SHA256SUMS.txt`. Versions containing `-` are published as prereleases.

The workflow output and release assets serve as the verification record, so no follow-up pull request is needed after publishing.
