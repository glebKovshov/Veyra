# Veyra 0.2.0-beta.32+mc1.21.11 - experimental prerelease

Beta.32 includes a post-beta.31 compatibility follow-up for the unmodified Sildur's Vibrant Shaders v2.02 Extreme `__DEFAULT__` profile. The fixes cover comment-aware varying and entity capability scans, VulkanMod's handling of `in`/`out` function parameters, lightmap sampling on the line pipeline, and the world-border entity-color fallback. Veyra remains an experimental compatibility subset; it does not provide full Iris parity or establish that complete third-party shaderpacks are playable.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.32+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Changes since beta.31

- Ignore GLSL varying, uniform, and capability declarations that occur only inside comments, so inactive shader code does not reserve locations or reject a shaderpack.
- Preserve GLSL function semantics while bypassing VulkanMod 0.6.8's broken attribute-parser path for `in` and `out` function parameters.
- Bind the shaderpack lightmap sampler for Veyra's basic line pipeline and world-border pipeline.
- Supply a transparent entity-color fallback for the world-border pipeline, whose vanilla layout has no overlay-color input.

## Validation and limits

The JDK 21 gate passed **531 tests, 0 failures/errors/skips**; 98 shader examples were preprocessed and 114 stages compiled directly to Vulkan SPIR-V. `validateShaders`, `build`, `packageExamplePacks`, and `compileGraphicsSmokeJava` passed. The pinned source audit covers four archives, 12 defaults and 63 profile/dimension cases: 864 geometry fragments accepted / 0 rejected and 18 active compute stages compiled / 0 rejected, with no audit errors. Source audit rows retain `visualValidation=false`.

On an NVIDIA RTX 3070, the unmodified pinned Sildur v2.02 Extreme `__DEFAULT__` profile completed resize, Nether transition, Overworld return, disable, re-enable, and resource reload, with 360,000 non-black pixels / 48 color buckets. This GPU smoke did not enable Khronos VVL and covers one profile/fixture. It does not certify the full pack or Iris parity. The release workflow runs the pinned source audit, Lavapipe Graphics matrix, JDK 21 build/tests, and distributable verification.
