# Veyra 1.0.0

First stable release of Veyra for Minecraft 1.21.11 and VulkanMod 0.6.8. It runs a checked subset of Iris shaderpack programs on VulkanMod and rejects unsupported features with a diagnostic, not a partly working pipeline.

## Changes since 0.2.3

- Iris compatibility: capability macros and optional-feature negotiation now match Iris. Scalar and float-vector custom uniforms resolve forward references, reject cycles and restore their registrations when you switch packs. Block-comment `const` settings and legacy render-target aliases are read. The cloud height comes from the camera environment.
- Rendering: current and previous reflection sampling projections are corrected, and the Iris-style horizon is drawn. Graphics-only custom images and custom noise textures are supported.
- Formats: packed and wide render targets keep their storage, including native `R11F_G11F_B10F` where the device supports it. Auxiliary color inputs use linear filtering. RGB targets keep alpha one through RGBA fallbacks and mipmapped snapshots.
- Vulkan: fixed clear, upload, depth and index dependencies, swapchain acquisition during loading overlays, screenshot readback ordering, and native resource/view cleanup.
- Swapchain recovery: the swapchain is rebuilt when a resize or restore reaches VulkanMod while the window surface is still 0x0. Before this fix the game could stay on a 10x10 placeholder until the window was resized again.
- Performance: shaderpack loading no longer repeats GLSL declaration scans on large uniform-only headers.

## Installation

Requires Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8 and Java 21 or newer.

Assets: the install JAR `veyra-1.0.0+mc1.21.11.jar`, a sources JAR, an example-packs ZIP and `SHA256SUMS.txt`. Put the install JAR beside VulkanMod and remove older Veyra JARs. Press F6 in game to select and configure a shaderpack. Veyra does not redistribute or modify shaderpack archives.

## Validation

- JDK 21 checks: 672 JUnit tests and seven Python tests pass, 105 example sources compile to 123 Vulkan SPIR-V modules, and the packaging/JAR checks pass. The pinned source audit covers 4 packs × 3 dimensions in 75 cases: 939 geometry programs and 18 active compute stages, none rejected.
- Matched Veyra/Iris 1.10.8 runs (RTX 3070) for each pinned profile. Each pair uses byte-identical shader files, the same resolved options, the same scene and a fixed animation clock. Iris confirmed each profile with zero user changes. Each Veyra run then completes a full lifecycle with zero Khronos core/synchronization errors and a clean teardown audit: forced 0x0 surface recovery, resize, Nether and back, disable/re-enable and resource reload.

  | Pack / profile | Shader files | RGB MAE vs Iris (full / wall / floor, of 255) |
  | --- | --- | --- |
  | Complementary Reimagined r5.9.3 HIGH | 426 | 4.08 / 4.18 / 3.08 |
  | Complementary Unbound r5.9.3 COMPLEMENTARY | 426 | 4.24 / 4.38 / 2.48 |
  | Sildur's Vibrant v2.02 Extreme, defaults | 137 | 3.13 / 1.15 / 3.36 |
  | BSL 10.1.8 HIGH, natural ocean scene | 292 | 3.64 / 4.23 / 2.45 |

- Hosted gates: the Lavapipe graphics matrix (54 fixtures with Vulkan validation, rollback, pack switching and readback), the source audit, the build and shader compilation. The publication workflow downloads its own assets and checks them against SHA256SUMS.txt and the JAR metadata.

## Known limits

- Pixel parity with Iris is not certified. The remaining differences come from screen-origin sampling phases, storage precision and VulkanMod's native primary attachment between the world and post phases. Water/reflection and temporal differences are still visible in matched frames.
- Up to 16 logical color buffers (Iris allows 32) and 8 simultaneous outputs. Geometry/tessellation stages, matrix and integer-vector custom uniforms, and unsupported native inputs are rejected with a diagnostic.
- Hardware validation covers an RTX 3070 and Lavapipe. Other GPUs, packs and profiles are untested.

See COMPATIBILITY.md for the full contract and STABILITY_1_0.md for the release evidence.
