# Shaderpack compatibility: development 0.2

This is a **source-only compatibility audit** with no GPU rendering. The pinned archives were checked by the [CI source-audit run](https://github.com/glebKovshov/Veyra/actions/runs/39166689354) at commit `340db0ca80010d42591383b13e12d76463467048`: 12 default configurations and 63 declared-profile/dimension configurations. All 5,178 stage sources in that matrix were discovered and preprocessed. Adapter acceptance below counts selected geometry fragment bridges only; it does not compile or render a full shaderpack. No listed external pack is currently supported as a complete rendering pipeline.

| Pack | Version | Options / profiles | Default stages preprocessed, Overworld / Nether / End | Default geometry fragments accepted / attempted | Current source blockers | In-game pack result |
| --- | --- | --- | --- | --- | --- | --- |
| Complementary Reimagined | r5.9.3 | 403 / 8 | 73/73, 73/73, 73/73 | 0 / 10 | `colortex0` sampler in 9 geometry fragments; cloud `blockEntityId`; additional geometry pipeline features | Not tested |
| Complementary Unbound | r5.9.3 | 403 / 8 | 73/73, 73/73, 73/73 | 0 / 10 | `colortex0` sampler in 9 geometry fragments; cloud `blockEntityId`; additional geometry pipeline features | Not tested |
| BSL | 10.1.8 | 383 / 5 | 63/63, 59/59, 63/63 | 1 / 9 | `noisetex` in 6 geometry fragments, `gaux2` in water, cloud `eyeBrightnessSmooth` | Not tested |
| Sildur's Vibrant | 2.02 Extreme | 115 / 0 | 42/42, 42/42, 42/42 | 0 / 5 | `colortex4` in 4 geometry fragments; cloud custom uniform `texture` | Not tested |

Adapter checks attempted terrain, water, entity/block/hand-family and cloud fragments; other bridges need VulkanMod pipeline-specific inputs. The BSL entity fragment using Iris' `varying vec4 color` now passes its adapter; the alias is bridged to VulkanMod's vertex color. Every tested pack still has rejected geometry fragments, unsupported geometry samplers or cloud uniforms, and incomplete pipeline coverage. Compute execution is not implemented. Default dimension conditions leave the inventoried compute programs inactive, while capability preflight rejects unsupported active stages before replacing a selected pack.

The exact source URLs and SHA-512 checksums are in [validation/shaderpack-inputs.json](validation/shaderpack-inputs.json); declared profile selections are listed in [validation/shaderpack-profile-matrix.json](validation/shaderpack-profile-matrix.json). The current machine-readable default and profile results, including rejection messages and dimensions, are in [validation/shaderpack-source-audit.json](validation/shaderpack-source-audit.json) and [validation/shaderpack-profile-audit.json](validation/shaderpack-profile-audit.json). CI downloads the archives temporarily and uploads only audit JSON, checksums and logs; the packs remain under their authors' licenses and are not included in Veyra.

The declared-profile matrix covers 24 dimension cases for each Complementary edition and 15 for BSL (63 cases total); all 4,429 profile-selected stage sources preprocess. Across the full 75-case matrix, the geometry adapters accept 18 and reject 699 fragment-program/configuration combinations. The accepted cases are BSL only; its other tested geometry fragments still fail adapter checks. The active-compute preflight reports 9 of 24 profile/dimension cases for each Complementary edition. BSL's five profiles keep the audited compute stage inactive; Sildur's declares no profiles in this input. This covers declared profiles, not arbitrary option combinations, and provides no visual validation.

## Reproduce source checks

Download the exact input versions legally from their source and verify the recorded checksums. With JDK 21, run:

```sh
./gradlew auditShaderPacks -PshaderPacks=/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--dimension=-1,/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--dimension=1,/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--profile=PROFILE_NAME,/absolute/path/pack.zip
```

The profile audit option selects one declared profile. The default matrix and separate profile report distinguish those selections; neither establishes that arbitrary options or complete pipelines render correctly. The same pinned source audit can be rerun from the repository's GitHub Actions workflow.

## Remaining release checks

The local editing environment exposes no `/dev/dri` device. GitHub CI's [Lavapipe client run](https://github.com/glebKovshov/Veyra/actions/runs/39166399772) passed Veyra's synthetic geometry-MRT/composite fixture: the active frame produced 222,934 magenta pixels and the shaders-disabled baseline produced 0, with no Vulkan validation errors during runtime. This does not render any third-party pack in the table. On process teardown, VulkanMod/Minecraft reports 10,031 `VUID-vkDestroyDevice-device-05137` child-object diagnostics; their owner has not been identified, and they are preserved in the CI artifact. Physical GPU testing, F6 UI interaction, popular-pack visual compatibility, water/sky/light/transparency, resize, resource reload, world/dimension/pack switching and sustained GPU-memory checks remain unverified. A main-branch stable release claiming Iris parity has not been published from this development branch.
