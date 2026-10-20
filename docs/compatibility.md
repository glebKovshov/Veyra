# Shader pack compatibility

This page records the current measured compatibility of popular third-party shader packs. It is updated whenever the pinned audit results change. Results for earlier versions can be found in the git history of the [`validation/`](../validation) reports.

> [!IMPORTANT]
> No third-party shader pack currently loads in Veyra. The numbers below are **source-level** results: they show how many fragment programs the adapters accept, not that a pack renders correctly.

## Current results

Measured on `0.2.0-beta.23`.

| Pack | Default geometry fragments accepted (Overworld, Nether, End) | Blockers | Load result |
| --- | --- | --- | --- |
| Complementary Reimagined r5.9.3 | 10/10, 10/10, 10/10 | Custom geometry vertex stages; `shadowcomp.csh` compute in some profiles | Rejected (capability error) |
| Complementary Unbound r5.9.3 | 10/10, 10/10, 10/10 | Custom geometry vertex stages; `shadowcomp.csh` compute in some profiles | Rejected (capability error) |
| BSL 10.1.8 | 9/9, 9/9, 9/9 | Custom geometry vertex stages | Rejected (capability error) |
| Sildur's Vibrant 2.02 Extreme | 1/5, 1/5, 1/5 | Custom geometry vertex stages; four translucent programs need the pack-specific `LightC` varying | Rejected (capability error) |

Across all 75 audited configurations (default, declared profiles, and three dimensions per pack), 5,178 shader stages are preprocessed, and **705 geometry fragment programs are accepted and 12 rejected**.

### Main blocker: geometry vertex stages

Every audited configuration contains an active custom `gbuffers_*.vsh` stage for a non-cloud program. Veyra still renders terrain and entities with VulkanMod's built-in vertex shaders, so it rejects these packs during capability validation instead of silently ignoring their vertex code. Executing pack geometry vertex stages is the main missing feature; see [Architecture → Known gaps](architecture.md#known-gaps).

### Other blockers

- **Complementary**: some declared profiles activate the `shadowcomp.csh` compute stage, and compute stages are not supported.
- **Sildur's Vibrant**: four translucent programs, in each of three dimensions, read `LightC`, which only the pack's own vertex stage produces.

## What the audit covers

The audit runs Veyra's loader and adapters against the original pack sources:

- pack discovery, option and profile evaluation, include expansion, and preprocessing of every selected stage;
- capability checks for unsupported stages (vertex, compute, geometry, tessellation);
- fragment adaptation for terrain, water, entity-family, and cloud programs.

It does **not** compile complete pipelines, bind descriptors, or render anything. Every record in the reports has `visualValidation: false`. The declared-profile matrix covers profiles that packs ship, not arbitrary option combinations.

## Inputs and reports

| File | Contents |
| --- | --- |
| [`validation/shaderpack-inputs.json`](../validation/shaderpack-inputs.json) | Source URLs and SHA-512 checksums of the pinned archives |
| [`validation/shaderpack-profile-matrix.json`](../validation/shaderpack-profile-matrix.json) | Declared profiles selected for the audit |
| [`validation/shaderpack-source-audit.json`](../validation/shaderpack-source-audit.json) | All 75 configurations: defaults and declared profiles in each dimension |
| [`validation/shaderpack-profile-audit.json`](../validation/shaderpack-profile-audit.json) | The 63 declared-profile configurations only |

The packs remain under their authors' licenses and are not included in this repository. The [Shaderpack audit](../.github/workflows/shaderpack-audit.yml) workflow downloads them temporarily, verifies the checksums, and uploads the reports and logs as artifacts.

## Reproducing

Download the exact archives listed in `shaderpack-inputs.json` and verify their checksums. Then, with JDK 21:

```sh
./gradlew auditShaderPacks -PshaderPacks=/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--dimension=-1,/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--dimension=1,/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--profile=PROFILE_NAME,/absolute/path/pack.zip
```

To run the full matrix, use `scripts/run_shaderpack_source_audit.py` or trigger the audit workflow on GitHub.

## Not yet verified

The following have not been tested with any third-party pack: rendering on a physical GPU, visual output, the F6 configuration screen, water, sky, lighting and transparency, window resizing, resource reloads, world, dimension and pack switching, and sustained GPU memory use.
