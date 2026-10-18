# Shaderpack compatibility: development 0.2

All results below are **source checks without a GPU**, using default pack settings in Overworld, Nether and End. Discovery or successful preprocessing is not evidence of playable rendering. No listed external pack is currently supported as a complete rendering pipeline.

| Pack | Version | Options / profiles discovered | Stages preprocessed, Overworld / Nether / End | Confirmed blockers | In-game result |
| --- | --- | --- | --- | --- | --- |
| Complementary Reimagined | r5.9.3 | 403 / 8 | 73/73, 73/73, 73/73 | Geometry color samplers/IDs, arbitrary vertex programs and MRT | Not tested; incompatible pipeline |
| Complementary Unbound | r5.9.3 | 403 / 8 | 73/73, 73/73, 73/73 | Geometry color samplers/IDs, arbitrary vertex programs and MRT | Not tested; incompatible pipeline |
| BSL | 10.1.8 | 383 / 5 | 63/63, 59/59, 63/63 | Noisetex/gaux2, extra varyings/uniforms and geometry MRT | Not tested; incompatible pipeline |
| Sildur's Vibrant | 2.02 Extreme | 115 / 0 | 42/42, 42/42, 42/42 | Geometry colortex4 sampler, cloud texture uniform and arbitrary geometry vertex programs | Not tested; incompatible pipeline |

Adapter checks attempted terrain, water, entity/block/hand-family and cloud fragments; other bridges need VulkanMod pipeline-specific inputs. Accepted geometry fragment counts were zero in these default-setting runs. Compute counts inventory available stages, including disabled programs; execution support has not been implemented. Dimension-specific program conditions disable the inventoried compute stages at these default settings. The capability preflight now refuses unsupported active stages before loading a replacement pack; this is an error report, not a compute implementation.

The exact source URLs and SHA-512 checksums are in [validation/shaderpack-inputs.json](validation/shaderpack-inputs.json). The machine-readable results, including rejection messages and dimensions, are in [validation/shaderpack-source-audit.json](validation/shaderpack-source-audit.json). These files contain metadata only; packs remain under their authors' licenses. The archives are not included in Veyra.

All declared quality profiles were additionally source-audited in each dimension: 24 cases each for Complementary Reimagined/Unbound and 15 for BSL, or 63 cases total. All 4,429 inventoried stage sources preprocessed. No tested geometry fragment passed the adapter checks. Nine cases in each Complementary edition enable the unsupported shadowcomp compute stage. BSL's five quality profiles kept that stage disabled. Sildur's declares no profiles in this input. [Profile audit results](validation/shaderpack-profile-audit.json) record every case. This covers declared profiles, not arbitrary combinations of individual options, and still provides no visual validation.

## Reproduce source checks

Download the exact input versions legally from their source and verify the recorded checksums. With JDK 21, run:

```sh
./gradlew auditShaderPacks -PshaderPacks=/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--dimension=-1,/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--dimension=1,/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--profile=PROFILE_NAME,/absolute/path/pack.zip
```

The profile audit option selects one declared profile. The default matrix and separate profile report distinguish those selections; neither establishes that arbitrary options or complete pipelines render correctly.

## Remaining release checks

The editing environment exposes no `/dev/dri` device and cannot run a real Vulkan client. Minecraft launch, F6 UI interaction, water/sky/light/transparency, resize, resource reload, world/dimension/pack switching, GPU memory stability and Vulkan validation-layer output are untested. These must be checked after the backend gaps in ARCHITECTURE.md are implemented. A main-branch stable release claiming Iris parity has not been published from this development branch.
