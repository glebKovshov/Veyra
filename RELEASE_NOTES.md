# Veyra 0.2.0-beta.29+mc1.21.11 — experimental prerelease

Beta.29 batches supported lightning, textured-sky, and world-border vertex execution. It remains an experimental compatibility subset; it does not provide full Iris parity or establish that complete third-party shaderpacks are playable.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.29+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Changes since beta.28

- Execute supported custom `gbuffers_lightning.vsh`, both native `gbuffers_skytextured.vsh` formats, and `gbuffers_textured.vsh` for the world border. Validate vertex-to-fragment varying links before pipeline creation.
- Specialize sky-textured `renderStage` variants for SUN, MOON, and VOID celestial draws and VOID End-sky draws. Support core `textureSize()` for supported 2D samplers.
- Keep native textured coordinates within Vulkan's vertex-output component limits.
- The beta.25 `CloudRenderer` Mixin crash was fixed in beta.26 and remains fixed in this release.

## Validation and known limits

Feature PR [#77](https://github.com/glebKovshov/Veyra/pull/77) merged into `main` at `e4f96f8`. Build [39551772481](https://github.com/glebKovshov/Veyra/actions/runs/39551772481), pinned source audit [39551772538](https://github.com/glebKovshov/Veyra/actions/runs/39551772538), and Graphics smoke [39551772493](https://github.com/glebKovshov/Veyra/actions/runs/39551772493) passed; all **34/34** Lavapipe fixtures passed.

The local versioned JDK 21 build passed **330 tests**; 85 example shaders were processed and 98 stages compiled directly to Vulkan SPIR-V. On an NVIDIA GeForce RTX 3070, the synthetic sky-stage fixture read back **14,249 active SUN pixels / 10,517 active MOON pixels / 0 after disabling Veyra**. These fixtures test named paths, not full third-party shaderpack visuals.

The pinned audit covers 4 archives, 63 profile/dimension cases, and 5,178 stages: **781 geometry fragments accepted / 8 rejected**. Remaining first source blockers include `gbuffers_textured` requesting the unavailable Minecraft lightmap sampler (72 records), Sildur's unsupported `normal` output type (3), and Sildur `shadowData` fragments in Nether and End. Selected Complementary profiles also declare unsupported `shadowcomp.csh` compute. Every audit record has `visualValidation=false`; no complete third-party shaderpack has been visually validated. See [COMPATIBILITY.md](COMPATIBILITY.md) for measured results.
