# Veyra 0.2.0-beta.47+mc1.21.11 - experimental prerelease

Beta.47 fixes loose shader-pack uniforms that were zero in sky, particle, weather and entity shaders, and honours Iris' shadow entity toggles. This restores valid values in the affected passes and lets the tested Complementary Unbound COMPLEMENTARY profile load; it does not establish full Iris visual parity.

## Changes

- **Loose uniforms:** VulkanMod gathers loose shader-pack uniforms (`sunAngle`, `worldTime`, `viewWidth`, ...) into a generated block that `RenderPipeline` draws never filled, so sky, particle, weather, entity and shadow-entity shaders read 0. Complementary derived a pre-dawn time of day from that: black sky, no clouds, night lighting. Those passes now read the real values (PR #127).
- **Shadow entity toggles:** `shadowEntities` and `shadowBlockEntities` from `shaders.properties` (option-preprocessed, default true) now skip entity or block-entity submissions in the shadow pass. Complementary disables block-entity shadows; Veyra previously still routed them to a `shadow_block` program that, under the COMPLEMENTARY profile, declares a writable `voxel_img` and disabled the pack (PR #128).

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.47+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Validation and known limits

- Iris 1.10.8 + Sodium 0.8.14 reference of the same Complementary Reimagined HIGH fixture: Veyra's sky, clouds, wall and layout match after #127. At 640×360, Veyra's Vulkan readback and Minecraft Screenshot API capture are pixel-identical (MAE 0 per RGB channel). Against Iris' Screenshot API capture, Veyra remains darker (mean RGB 57.84/58.45/52.45 vs 121.49/108.53/80.95); the capture method does not explain the gap, and its rendering/color-space cause is unconfirmed.
- RTX 3070 lifecycle runs (resize, Nether and return, disable/re-enable, resource reload): BSL HIGH 360,000 px / 124 buckets, Sildur's Vibrant default 360,000 / 122, Complementary Unbound HIGH 358,109 / 92, Unbound COMPLEMENTARY 359,042 / 93 (previously failed). All passed.
- PRs #127 and #128 each passed all 57 hosted checks before merging. The release workflow repeats the pinned source audit, full Lavapipe matrix, versioned build/tests and distributable verification.
- Not certified: exact Iris visual parity, natural-world visuals and Sildur sign text. The earlier one-off Windows shutdown exit (0xC0000409) remains unexplained; a later candidate smoke exited normally but did not reproduce that failure or prove its fix.

Veyra remains an experimental Iris-compatible subset and does not claim stable Iris parity. See COMPATIBILITY.md and ARCHITECTURE.md for the measured scope.
