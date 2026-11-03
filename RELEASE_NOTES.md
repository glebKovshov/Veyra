# Veyra 1.0.0 release preparation

Unpublished candidate for Minecraft 1.21.11 and VulkanMod 0.6.8. Production metadata remains 0.2.3 until the final stability gates pass.

## Changes

- Fix Iris capability negotiation and scalar/vector custom-uniform dependencies, including forward references, cycle rejection and registry restoration when switching packs.
- Support graphics-only custom images and custom noise textures with explicit ownership and synchronization.
- Correct reflection sampling projections, horizon coverage, camera cloud height, active block-comment settings and legacy color-target directives.
- Preserve packed/wide storage, including native `R11F_G11F_B10F` on supported devices, filtered color inputs and RGB alpha semantics in primary/auxiliary targets and mipmapped snapshots.
- Fix loading-overlay swapchain acquisition, clear/upload/depth/index dependencies, screenshot readback ordering and resource/view cleanup.
- Reduce repeated GLSL declaration scans and extend GPU regressions for invalid replacement, switching packs and native screenshot equality.

## Installation

Requires Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.

The eventual release will provide the install JAR, a sources JAR, an example-packs ZIP and `SHA256SUMS.txt`. Use the install JAR beside VulkanMod, remove older Veyra JARs, and press F6 to select/configure a shader pack. Shaderpack archives are not redistributed or modified by Veyra.

## Validation and limits

- Current local production-code checks pass 671 JUnit tests, seven Python tests, 105 example sources / 123 Vulkan SPIR-V compilations, packaging and JAR verification. The pinned source audit passes 75 profile/dimension cases, 939 geometry programs and 18 active compute stages. Source acceptance does not establish runtime visuals.
- All four selected profiles completed RTX 3070 lifecycles with zero Vulkan core/sync/teardown errors on the 0b94709 baseline. The native packed-HDR follow-up passes BSL's full lifecycle and the focused rollback/switch/readback fixture; the remaining three repeats, hosted checks and release-ref checks are pending. See STABILITY_1_0.md and WORK_SUMMARY.md for exact evidence.
- Publication requires the source audit, Lavapipe graphics matrix, build/tests, shader compilation and distributable verification. The publication workflow also downloads its assets and verifies checksums and JAR contents.
- Veyra supports a checked subset of Iris programs, with 16 color targets and explicit unsupported-feature diagnostics. World rendering still uses VulkanMod's native primary attachment, so HDR range/quantization across world/post boundaries can differ from Iris. Full visual parity and arbitrary pack/profile/GPU compatibility are not certified. See COMPATIBILITY.md for remaining rendering differences and measured limits.
