# Veyra 0.2.0-beta.28+mc1.21.11 — experimental prerelease

Beta.28 batches Iris render-stage constants and per-program entity identifiers for supported custom vertex shaders. It remains an experimental compatibility subset; it does not provide full Iris parity or establish that complete third-party shaderpacks are playable.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.28+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Changes since beta.27

- Add Iris `MC_RENDER_STAGE_*` macros and bind `renderStage` to the available sky, stars, and sunrise/sunset pipeline values.
- Bind `entityId`, `blockEntityId`, and `currentRenderedItemId` in supported custom entity, block, and hand vertex stages. Include selected identifier values in the pipeline cache identity.
- Remove unused `mc_Entity` declarations when shader code does not read the attribute. Active reads still produce a capability error because Minecraft's `NEW_ENTITY` vertex format does not provide it.
- Skip the legacy OptiFine-only `gbuffers_entities_glowing` program, which Iris does not discover.
- The beta.25 `CloudRenderer` Mixin crash was fixed in beta.26 and remains fixed in this release.

## Validation and known limits

PR [#74](https://github.com/glebKovshov/Veyra/pull/74) merged as `9a8b0d8`. Build [39542873289](https://github.com/glebKovshov/Veyra/actions/runs/39542873289), pinned source audit [39542873265](https://github.com/glebKovshov/Veyra/actions/runs/39542873265), and Graphics smoke [39542873210](https://github.com/glebKovshov/Veyra/actions/runs/39542873210) passed. CI Graphics smoke passed **30/30** Lavapipe fixtures.

The local JDK 21 build passed 323 tests; 82 example shaders were processed and 86 stages compiled to Vulkan SPIR-V. On an NVIDIA GeForce RTX 3070, synthetic entity, sky-basic, and currentRenderedItemId fixtures read back respectively **18,532**, **226,295**, and **13,984** active pixels, with **0** for each after disabling the pack. These fixtures test named paths, not full third-party shaderpack visuals.

The pinned audit covers 4 archives, 75 profile/dimension cases, and 5,178 stages: **781 geometry fragments accepted / 8 rejected**. Remaining first source blockers include `gbuffers_lightning.vsh` (54 cases), `gbuffers_skytextured.vsh` (13), `gbuffers_textured.vsh` (6), and Sildur `shadowData` (2). Selected Complementary profiles also declare unsupported `shadowcomp.csh` compute. Every audit record has `visualValidation=false`; no complete third-party shaderpack has been visually validated. See [COMPATIBILITY.md](COMPATIBILITY.md) for the measured matrix.
