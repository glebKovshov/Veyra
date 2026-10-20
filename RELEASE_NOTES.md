# Veyra 0.2.0-beta.22+mc1.21.11 - experimental prerelease

This release adds color-buffer inputs and explicit feedback snapshots for supported water and entity-family geometry. It does not claim full Iris parity, stable-release readiness, or correct rendering of complete third-party shaderpacks.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.22+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.21

- Add up to six distinct auxiliary `colortex1..15` inputs to supported water and entity-family geometry.
- When a sampled image is also an output attachment, snapshot its current contents to a separate Vulkan image before drawing, while keeping the source image writable as an output.
- Add water and entity feedback examples. PR #51 merged at `bc253f51ece852688b5b26b59b877effea7ff091`; its Build, pinned source audit, and 22/22 Lavapipe Graphics smoke workflows passed.

## Validation

- Entity feedback readback: 18,648 active magenta pixels, 0 disabled pixels, and 0 runtime Vulkan validation errors. Vulkan teardown diagnostics remain.
- The pinned source audit covers 4 archives, 75 records, and 5,178 stages: 705 geometry fragments accepted / 12 rejected; all records have `visualValidation=false`.
- The 12 remaining source rejections are four Sildur translucent geometry programs that need its pack-specific `LightC` varying. Selected Complementary profiles still need the unsupported `shadowcomp.csh` compute stage.
- Release CI rebuilds and tests the final versioned artifact from the release tag and verifies its contents and checksum manifest.

## Known limits

- Complementary Reimagined and Unbound accept 10/10 default geometry fragments in each tested dimension; selected profiles still require the unsupported `shadowcomp.csh` compute stage.
- BSL 10.1.8 accepts 9/9 default geometry fragments in each tested dimension. The complete pack has not been visually tested.
- Sildur's Vibrant 2.02 Extreme accepts 3/15 default geometry fragments; its four translucent programs require `LightC` from a custom vertex stage Veyra does not implement.
- No complete third-party shaderpack visual validation or physical-GPU testing has been completed. Vulkan teardown diagnostics and the broader stable-release goal remain unresolved.

Unsupported active features are diagnosed instead of silently filled with invented values. See `COMPATIBILITY.md` and `VALIDATION.md` for scope and measured results.
