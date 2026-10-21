# Veyra 0.2.0-beta.27+mc1.21.11 — experimental prerelease

Beta.27 batches supported custom vertex execution for damaged-block and sky-basic pipelines. It remains an experimental subset and does not provide full Iris compatibility or establish that a complete third-party shaderpack is playable.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.27+mc1.21.11.jar` in the instance mods folder alongside VulkanMod. Keep only one Veyra JAR installed; do not install the sources JAR.
- Optional example packs can be extracted into shaderpacks. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; shader pack archives are not modified.

## Changes since beta.26

- Add a supported custom `gbuffers_damagedblock.vsh` path after Minecraft's native crumbling setup. Match the `BLOCK` vertex format, including integer `UV2`; map legacy lightmap input correctly and adapt VulkanMod's crumbling shader input before Vulkan pipeline compilation.
- Add supported custom `gbuffers_skybasic.vsh` execution for sky, stars, and sunrise/sunset native pipelines. Validate actual native vertex formats and linked varyings.
- Add damaged-block and sky-basic example packs, compiler and adapter regression coverage, source-audit support, and real-client graphics fixtures.
- Update the smoke harness to use the correct sky fixture directory, framebuffer readback, and upward-facing camera.

## Validation and known limits

PR [#70](https://github.com/glebKovshov/Veyra/pull/70) merged as `17409ce`; PR [#71](https://github.com/glebKovshov/Veyra/pull/71) merged as `044a953`. Build [39534071885](https://github.com/glebKovshov/Veyra/actions/runs/39534071885), pinned source audit [39534071977](https://github.com/glebKovshov/Veyra/actions/runs/39534071977), and graphics smoke [39534071890](https://github.com/glebKovshov/Veyra/actions/runs/39534071890) passed; graphics smoke passed all **30/30** Lavapipe fixtures, including sky-basic and damaged-block validation.

The local JDK 21 build passed 313 tests; 82 example shaders were processed and 86 stages compiled to Vulkan SPIR-V. On NVIDIA GeForce RTX 3070, the sky-basic fixture produced 226,624 active magenta pixels and 0 with shaders disabled. These synthetic fixtures verify specific paths, not full shaderpack visuals.

The pinned source audit covers 4 archives, 75 profile/dimension cases, and 5,178 stages: **781 geometry fragments accepted / 8 rejected**. Remaining first source blockers include `entityId` (54 cases), `renderStage` (12), `mc_Entity` (6), `shadowData` (2), and unsupported `gbuffers_skytextured.vsh` (1); selected Complementary profiles also declare unsupported `shadowcomp.csh`. Every audit record has `visualValidation=false`. No complete third-party pack has been visually validated; the stable-release goal remains incomplete.