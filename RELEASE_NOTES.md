# Veyra 0.2.0-beta.31+mc1.21.11 - experimental prerelease

Beta.31 batches custom weather and translucent-particle vertex support with selected shadow-compute, shader-pack textures on supported terrain, shadow, and entity geometry, BSL shader-compatibility, and shadow-transition work. It also adds Iris render-relative and indirect `shadowcomp` dispatch, with core Vulkan features enabled through the `VkPhysicalDeviceFeatures2` create-info path. It remains an experimental compatibility subset; it does not provide full Iris parity or establish that complete third-party shaderpacks are playable.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.31+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Changes since beta.30

- Execute supported custom `gbuffers_weather.vsh` and `gbuffers_particles_translucent.vsh` stages. Add generic defaults for the supported world-border `gbuffers_textured` path and make shader uniform/sampler scans comment-safe.
- Add selected `shadowcomp` compute execution with fixed workgroups and 3D shader-pack images, shadow color MRT, supported shadow vertex/entity stages, selected-profile storage buffers, and expanded post texture slots.
- Resolve Iris `workGroupsRender` against the current render target and support `indirect.<pass>` dispatch from validated SSBO command buffers, with shader-write to indirect-read synchronization.
- Enable supported core Vulkan features through a `VkPhysicalDeviceFeatures2` node while preserving VulkanMod's existing Vulkan 1.1 and dynamic-rendering feature chain.
- Load shader-pack PNGs for configured global `customTexture.*` aliases and stage-scoped `texture.<stage>.<sampler>` mappings on supported terrain, shadow, and entity geometry. Resolve entity samplers against the selected entity program, and prepare pack-local textures before pipeline selection even when the pack was restored from settings. Honor `.mcmeta` blur/clamp settings; this supports the `texture.gbuffers.gaux4` input used by Complementary Unbound.
- Bind a neutral shadow input until Vulkan records a completed shadow map, and skip dependent post passes during that transition. This prevents early `shadowcolor0` reads from using an incomplete map.
- Adapt BSL's aliased fog uniforms and `Fog(...)` helper, preserve pack-defined `texture2DShadow(...)` functions, and provide neutral post shadow inputs in dimensions where BSL disables shadow rendering.

## Validation and known limits

The local JDK 21 gate passed **512 tests** with no failures, errors, or skips; it processed 97 example shaders, compiled 113 stages to Vulkan SPIR-V, built the mod, compiled graphics-smoke sources, and packaged the example packs. The pinned audit covers 4 archives, 12 defaults, 63 profile/dimension cases, and 5,178 stages: **864 geometry fragments accepted / 0 rejected** and **18 active compute stages compiled / 0 rejected**. Every audit row has `visualValidation=false`.

Complementary Reimagined and Unbound r5.9.3 each passed one selected `COMPLEMENTARY` profile smoke and resize/dimension/disable/re-enable/reload lifecycle on an RTX 3070. Each synthetic-scene capture had 360,000 non-black pixels and 54 color buckets. BSL v10.1.8 `HIGH` passed the same selected-profile lifecycle with 225,164 non-black pixels and 38 color buckets. The `entity-custom-texture` fixture passed with 18,293 active / 0 disabled green pixels and `marker` bound at descriptor binding 6. The RTX 3070 `custom-image-compute` fixture produced 222,994 active magenta pixels before the final `VkPhysicalDeviceFeatures2` correction; that host has no Vulkan validation layer, so this result does not validate the corrected feature chain. Focused runs do not certify complete packs or full Iris parity. Publication requires the release workflow's pinned audit and full Lavapipe graphics smoke to pass before building and verifying the distributable.

Custom pack textures are currently wired to supported terrain, shadow, and entity geometry. Compute support remains limited to `shadowcomp` using 3D images with fixed or render-relative workgroups and indirect dispatch; other compute families, arbitrary image formats, and complete third-party pack compatibility remain unsupported. See [COMPATIBILITY.md](COMPATIBILITY.md) for measured results and limits.
