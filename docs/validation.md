# Validation

This page describes how Veyra is tested, what each check proves, and what remains unverified. It reflects the current state of `main`. Per-release results (CI runs, checksums) are attached to each [GitHub release](https://github.com/glebKovshov/Veyra/releases) and kept in the workflow logs.

## Automated checks

| Check | Workflow | Runs on | Proves |
| --- | --- | --- | --- |
| Build and unit tests | [Build](../.github/workflows/build.yml) | Every push and pull request | The mod compiles and remaps; JUnit regressions pass |
| Shader compilation | Build | Every push and pull request | Example shaders preprocess, and those that do not need VulkanMod's descriptor conversion compile to SPIR-V with shaderc |
| Distributable check | Build | Every push and pull request | `scripts/verify_jar.py` finds the expected metadata, notices, licenses, and refmap in the JAR |
| Pinned source audit | [Shaderpack audit](../.github/workflows/shaderpack-audit.yml) | Pull requests touching the shader loader | The adapters' results on four pinned third-party packs; see [Compatibility](compatibility.md) |
| Graphics smoke | [Graphics smoke](../.github/workflows/graphics-smoke.yml) | Pull requests touching the renderer or fixtures | A real Minecraft client renders each fixture on Lavapipe; the frame is read back and checked, and no Vulkan validation error occurs before shutdown |
| Release | [Release](../.github/workflows/release.yml) | `v*` tags and `release/**` branches | All of the above on the exact release commit, then the JAR, sources, example packs, and `SHA256SUMS.txt` are published |

### Current results

As of `0.2.0-beta.23`:

- 244 JUnit tests pass with no failures, errors, or skips.
- 53 example shaders are preprocessed and 50 are compiled directly to SPIR-V.
- All 22 graphics smoke jobs pass: 21 feature fixtures (marked **CI** in [Example packs](examples.md)) plus a vanilla baseline with shaders disabled. No fixture reports a runtime Vulkan validation error.
- The pinned audit accepts 705 and rejects 12 geometry fragment programs across 75 configurations.

## Graphics smoke fixtures

Each fixture starts Minecraft under Xvfb with Mesa's Lavapipe software Vulkan driver and the Khronos validation layer. `scripts/prepare_graphics_smoke.py` builds an original test world and selects the fixture's example pack. The client renders the scene, reads back pixels, and writes `build/reports/graphics-smoke/result.json`.

A job fails if the expected colors are not found or if a validation error appears in the log before the test player disconnects. Fixtures are designed so that a missing, aliased, or late resource cannot pass. For example, the shadow depth fixtures author different depths in `shadowtex0` and `shadowtex1` and require both at the same coordinate.

### Known issue: teardown diagnostics

When Minecraft destroys the Vulkan device on exit, the validation layer reports about 10,000 undestroyed child objects. The vanilla baseline (shaders disabled) reports almost the same number, so most of these belong to Minecraft or VulkanMod. Fixtures that use Veyra report a few more `VkPipeline` handles than the baseline, and their owner has not been identified. These diagnostics are kept in the uploaded artifacts but do not fail the job. Process-wide resource cleanup is therefore **not** validated.

## Not yet verified

- Any complete third-party shader pack, visually or otherwise (none currently loads).
- Physical GPUs from any vendor. All runtime testing uses Lavapipe.
- Interactive use of the F6 menu and configuration screens.
- Window resizing, resource reloads, world, dimension, and pack switching under load.
- Sustained GPU memory use and frame time. No performance improvement is claimed.

Manual testing on a real GPU with Minecraft 1.21.11, Fabric Loader 0.18.4+, and VulkanMod 0.6.8 is the most valuable missing evidence. Useful scenarios: pack selection, refresh after editing or removing a pack, disabling shaders, resource reload, resizing, changing worlds and dimensions, depth snapshots, and shadows.

## Running checks locally

```sh
./gradlew build validateShaders packageExamplePacks
python3 scripts/verify_jar.py
```

The graphics smoke and source audit need network access and, for graphics, a Vulkan driver. They are easiest to run through GitHub Actions (`workflow_dispatch`). See [Compatibility → Reproducing](compatibility.md#reproducing) for running the audit locally.
