# Veyra 0.2.0-beta.23+mc1.21.11 - experimental prerelease

This release reports active custom non-cloud geometry vertex stages as unsupported before attempting to load a shader pack. Veyra still uses VulkanMod's built-in terrain and entity vertex stages. It does not implement general geometry vertex-stage execution, full Iris parity, or stable-release compatibility.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.23+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.22

- Fail explicitly on an active non-cloud `gbuffers_*.vsh` stage when its matching fragment program is present. This avoids silently running VulkanMod's built-in vertex shader while implying the pack's vertex shader ran.
- Disabled programs are not rejected. The existing cloud and fullscreen post vertex adapters remain supported.
- Merged in #54. Build, pinned source audit, and all 22 Lavapipe graphics fixtures passed.

## Validation

- The merged code passed local JDK 21 `build validateShaders packageExamplePacks compileGraphicsSmokeJava --rerun-tasks`: 244 tests, 0 failures/errors/skips; 53 example shaders preprocessed and 50 compiled directly to Vulkan SPIR-V.
- The pinned audit covers 4 archives, 75 records, and 5,178 stages: 705 geometry fragments accepted / 12 rejected. Every record reports an active unsupported geometry vertex stage as a capability error, and all retain `visualValidation=false`.
- Release CI rebuilds and tests the final versioned artifact, reruns the pinned audit and Lavapipe fixtures, verifies the distributable, and publishes SHA-256 checksums.

## Known limits

- General geometry `gbuffers_*.vsh` execution is not implemented. All 75 pinned-pack profile/dimension audit records currently report a whole-pack capability error for an active custom vertex stage. The fragment adapter counts above are source-level results, not evidence those complete packs load.
- Complementary Reimagined and Unbound still need `shadowcomp.csh` compute execution in selected profiles.
- Sildur's Vibrant 2.02 Extreme has four translucent programs that need its pack-specific `LightC` varying from a custom vertex stage.
- No complete third-party shaderpack visual validation or physical-GPU testing has been completed. Vulkan teardown diagnostics and the broader stable-release goal remain unresolved.

See the [changelog](https://github.com/glebKovshov/Veyra/blob/main/CHANGELOG.md), [compatibility results](https://github.com/glebKovshov/Veyra/blob/main/docs/compatibility.md), and [validation status](https://github.com/glebKovshov/Veyra/blob/main/docs/validation.md) for details.
