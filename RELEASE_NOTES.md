# Veyra 0.2.0-beta.31+mc1.21.11 - experimental prerelease

Beta.31 batches custom weather and translucent-particle vertex support with selected shadow-compute, shader-pack textures on supported terrain, shadow, and entity geometry, BSL shader-compatibility, and shadow-transition work. It also adds Iris render-relative `workGroupsRender` and validated `indirect.<pass>` dispatch for `shadowcomp`. Veyra requests supported core Vulkan features through a separate `pEnabledFeatures` block while retaining VulkanMod's Vulkan 1.1 and dynamic-rendering `pNext` chain. It remains an experimental compatibility subset; it does not provide full Iris parity or establish that complete third-party shaderpacks are playable.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.31+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Changes since beta.30

- Execute supported custom `gbuffers_weather.vsh` and `gbuffers_particles_translucent.vsh` stages. Add generic defaults for the supported world-border `gbuffers_textured` path and make shader uniform/sampler scans comment-safe.
- Add selected `shadowcomp` compute execution with fixed workgroups and 3D shader-pack images, shadow color MRT, supported shadow vertex/entity stages, selected-profile storage buffers, and expanded post texture slots.
- Resolve Iris `workGroupsRender` against the current render target and support `indirect.<pass>` dispatch from validated SSBO command buffers, with shader-write to indirect-read synchronization.
- Request supported core Vulkan features through a separate `pEnabledFeatures` block while preserving VulkanMod's Vulkan 1.1 and dynamic-rendering `pNext` chain.
- Match VulkanMod framebuffer render dimensions to the selected mip view, fixing block-atlas render-area validation errors.
- Load shader-pack PNGs for configured global `customTexture.*` aliases and stage-scoped `texture.<stage>.<sampler>` mappings on supported terrain, shadow, and entity geometry. Resolve entity samplers against the selected entity program, and prepare pack-local textures before pipeline selection even when the pack was restored from settings. Honor `.mcmeta` blur/clamp settings; this supports the `texture.gbuffers.gaux4` input used by Complementary Unbound.
- Bind a neutral shadow input until Vulkan records a completed shadow map, and skip dependent post passes during that transition. This prevents early `shadowcolor0` reads from using an incomplete map.
- Adapt BSL's aliased fog uniforms and `Fog(...)` helper, preserve pack-defined `texture2DShadow(...)` functions, and provide neutral post shadow inputs in dimensions where BSL disables shadow rendering.

## Validation and known limits

The combined #85+#86 local JDK 21 preflight gate passed **522 tests** with no failures, errors, or skips; it processed 97 example shaders, compiled 113 stages to Vulkan SPIR-V, compiled graphics-smoke sources, built the mod, and packaged the example packs. The pinned audit covers 4 SHA-512-verified archives, 12 defaults, 63 profile/dimension cases, and 5,178 stages: **864 geometry fragments accepted / 0 rejected** and **18 active compute stages compiled / 0 rejected**. Every audit row has `visualValidation=false`; the audit demonstrates source processing and compilation, not pack visuals.

On an NVIDIA RTX 3070, selected Complementary Reimagined and Unbound r5.9.3 `COMPLEMENTARY` profile lifecycle fixtures and BSL v10.1.8 `HIGH` lifecycle fixture passed. The focused `entity-custom-texture` fixture passed with 18,293 active / 0 disabled green pixels and `marker` bound at descriptor binding 6. On exact #85 head `8466a47`, `basic-custom-image` passed with 466 active green pixels and `entity-custom-image` passed with 18,410 active / 0 disabled green pixels; both reported `vertexPipelineStoresAndAtomics=true` and `shaderStorageImageExtendedFormats=true`. These are selected and synthetic tests, not whole-pack results.

The Sildur's Vibrant 2.02 Extreme harness currently remains incomplete. A full-pack RTX 3070 harness attempt produced a black final frame after deferred; its cause remains unconfirmed. Separately, an instrumented `VeyraSmoke` ambient profile reported `framemod8=6` and passed a focused assertion with 24,550 non-black pixels / 69 color buckets, but it did not run the pack lifecycle. A later `finalclass` probe failed with a nearly uniform frame (230,400 non-black pixels / 23 color buckets). No whole-pack Sildur visual compatibility is claimed. Later RTX runs enabled Khronos validation: the mip fix cleared render-area 06079/06080 errors, while two swapchain-acquire diagnostics and the existing teardown leak remain recorded. Hosted Lavapipe Graphics remains required for the final commit. Focused runs do not establish full Iris parity. Publication still requires the release workflow's pinned audit, Lavapipe graphics smoke, build, tests, and distributable verification.

Custom pack textures are currently wired to supported terrain, shadow, and entity geometry. Compute support remains limited to `shadowcomp` using 3D images with fixed or render-relative workgroups and indirect dispatch; other compute families, arbitrary image formats, and complete third-party pack compatibility remain unsupported. See [COMPATIBILITY.md](COMPATIBILITY.md) for measured results and limits.
