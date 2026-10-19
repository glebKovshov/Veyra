# Shaderpack compatibility: development 0.2

This is a **source-only compatibility audit**. It does not compile complete shaderpack pipelines or render third-party packs. The pinned archives were checked by the [CI source-audit run](https://github.com/glebKovshov/Veyra/actions/runs/39178344303) at code commit `e3ce1ea7b349e9971e5f4f5e0fa6a3cd31bd9853`: 12 default configurations and 63 declared-profile/dimension configurations. All 5,178 selected stage sources were discovered and preprocessed. The adapters accepted 18 and rejected 699 geometry fragment/configuration combinations. None of the listed packs has a complete pipeline confirmed in-game.

| Pack | Version | Options / profiles | Default stages preprocessed, Overworld / Nether / End | Default geometry fragments accepted / attempted | Current source blockers | In-game pack result |
| --- | --- | --- | --- | --- | --- | --- |
| Complementary Reimagined | r5.9.3 | 403 / 8 | 73/73, 73/73, 73/73 | 0 / 10 | Active `specular` reads in terrain/entity/block/hand programs; `depthtex1` and `colortex5` in water; cloud/spider-eye `blockEntityId`; `shadowcomp.csh` compute in selected profiles | Not tested |
| Complementary Unbound | r5.9.3 | 403 / 8 | 73/73, 73/73, 73/73 | 0 / 10 | Active `specular` reads in terrain/entity/block/hand programs; `depthtex1` and `colortex5` in water; cloud/spider-eye `blockEntityId`; `shadowcomp.csh` compute in selected profiles | Not tested |
| BSL | 10.1.8 | 383 / 5 | 63/63, 59/59, 63/63 | 1 / 9 | Active `shadowtex0` / `shadowtex1` / `gaux2` reads; pack-specific `entityId`, `blockEntityId`, `bedrockLevel`, `cloudHeight`, and cloud `texture` uniforms | Not tested |
| Sildur's Vibrant | 2.02 Extreme | 115 / 0 | 42/42, 42/42, 42/42 | 0 / 5 | `colortex4` in translucent geometry programs; cloud custom uniform `texture` | Not tested |

The adapter checks cover terrain, water, entity/block/hand-family and cloud fragments. The BSL entity fragment using Iris' `varying vec4 color` is accepted and bridged to VulkanMod's vertex color. `noisetex` is accepted across this source matrix, and the BSL cloud `eyeBrightnessSmooth` blocker is resolved in the current code. Unused sampler declarations, including `colortex0` when the shader never reads it, are removed before adapter validation; active reads from unsupported samplers still reject that program. These source results do not confirm sampler descriptors, lighting, image layouts, or rendered output. Other unsupported custom uniforms and samplers remain; see each row's generated rejection details in the machine-readable report.

The exact source URLs and SHA-512 checksums are in [validation/shaderpack-inputs.json](validation/shaderpack-inputs.json); declared profile selections are listed in [validation/shaderpack-profile-matrix.json](validation/shaderpack-profile-matrix.json). Default results and profile results are in [validation/shaderpack-source-audit.json](validation/shaderpack-source-audit.json) and [validation/shaderpack-profile-audit.json](validation/shaderpack-profile-audit.json). CI downloads the archives temporarily and uploads audit JSON, checksums and logs; the packs remain under their authors' licenses and are not included in Veyra.

The declared-profile matrix covers 24 dimension cases for each Complementary edition and 15 for BSL (63 cases total); all 4,429 profile-selected stage sources preprocess. Across all 75 cases, the geometry adapters accept 18 and reject 699 fragment-program/configuration combinations. The accepted cases are BSL only; its other tested geometry fragments still fail adapter checks. Active compute preflight rejects 9 of 24 profile/dimension cases for each Complementary edition. BSL's five profiles keep the inventoried compute stage inactive; Sildur's declares no profiles in this input. All 75 cases have `visualValidation: false`. This covers declared profiles, not arbitrary option combinations.

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

GitHub CI's [Lavapipe client run 39178344283](https://github.com/glebKovshov/Veyra/actions/runs/39178344283) passed Veyra's synthetic terrain-MRT/composite fixture: the active frame contained 222,857 magenta pixels and the shaders-disabled baseline contained 0. The runtime-validation file is empty. At process teardown, VulkanMod/Minecraft reports 10,031 `VUID-vkDestroyDevice-device-05137` child-object diagnostics; ownership has not been identified, so clean device teardown is not established. The fixture does not render any third-party pack. Physical GPU testing, F6 UI interaction, popular-pack visual compatibility, water/sky/light/transparency, resize, resource reload, world/dimension/pack switching and sustained GPU-memory checks remain unverified. The published 0.1.1 release predates this development branch; no main-branch release claiming Iris parity has been published.
