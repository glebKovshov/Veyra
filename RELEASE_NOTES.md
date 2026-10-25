# Veyra 0.2.0-beta.48+mc1.21.11 - experimental prerelease

Beta.48 fixes a Vulkan depth-space mismatch in shader-pack shadow geometry and selects Iris' shadow projection for entity shadow vertex mains. Complementary Reimagined HIGH is substantially brighter than the beta.47 capture, though it remains darker than Iris; this release does not claim full visual parity.

## Changes

- **Shadow depth:** Terrain and entity shadow pack vertex stages now map OpenGL clip depth `[-w,w]` to Vulkan zero-to-one after the pack entry point. Entity shadow legacy projection forms (`ftransform()`, `gl_ProjectionMatrix` and `gl_ModelViewProjectionMatrix`) use Iris `shadowProjection` (#132).
- **Loose uniforms and shadow toggles:** Shader-pack loose uniforms now receive VulkanMod suppliers in geometry pipelines, and `shadowEntities` / `shadowBlockEntities` options are honored (#127, #128).
- **Shutdown:** Drain deferred Vulkan image frees before allocator destruction (#130).
- **Sign text routing:** Carry block-entity text identity to the renderer, select Iris `BlockTrans`, load its pack vertex stage, and preserve native alpha cutoff (#131). Sildur glyph readability is still unverified.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.48+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack, then use Configure / Apply for supported options and profiles.

## Validation and known limits

- On an RTX 3070, pinned Complementary Reimagined r5.9.3 HIGH passed the 640x360 generated-scene smoke (230,400 non-black pixels / 116 color buckets). Compared with Iris 1.10.8 + Sodium 0.8.14, candidate mean RGB was 106.82/98.85/72.82 vs 121.49/108.53/80.95; mean absolute error was 17.69. The remaining brightness difference is unresolved.
- Selected-profile lifecycle evidence for BSL HIGH, Sildur default, Complementary Unbound HIGH and COMPLEMENTARY is recorded in `COMPATIBILITY.md`. These runs use generated scenes and do not certify natural worlds or whole packs. Sildur sign glyph readability remains unverified.
- Exact-head PR #132 checks passed: Build, pinned shaderpack source audit, and all 30 Lavapipe Graphics jobs. The full JDK 21 gate passed 595 tests, 104 example shaders and 122 Vulkan SPIR-V compilations.
- A previous Windows shutdown ended once with status `0xC0000409`; a later candidate run exited normally after #130, but the historical status is not fully explained.

Veyra remains an experimental Iris-compatible subset. Full Iris visual parity and stable compatibility are not claimed.
