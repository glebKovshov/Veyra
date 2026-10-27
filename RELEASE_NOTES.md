# Veyra 0.2.0+mc1.21.11

Veyra runs a documented subset of Iris/OptiFine shader-pack programs on VulkanMod. This release adds the 0.2 rendering pipeline, shader-pack options and profiles, compute resources, and the lighting, entity and shadow corrections developed through the beta series. Supported profiles and measured limits are listed in COMPATIBILITY.md.

## Changes

- Load ZIP and directory shader packs, select them with F6, and configure saved per-pack options and quality profiles.
- Run supported terrain, entity, block-entity, hand, sky, cloud, weather, particle and post programs with checked vertex/fragment interfaces, MRT and depth inputs.
- Support the documented compute families, shared custom images, SSBOs and validated direct/indirect dispatch.
- Feed live loose uniforms, honor `oldLighting` and shadow-entity toggles, and convert entity color overlays with Iris semantics.
- Preserve OpenGL shadow-map texture coordinates and clip depth on Vulkan, including native vertex fallbacks and face winding. The natural golem cast shadow now appears in the matched Complementary HIGH scene.
- Reconstruct missing fluid normals, align post-pass projection coordinates and shader-pack inverse projections, and preserve world and hand depth for final passes.
- Restore Iris shadow-view rotations and camera snapping, synchronize terrain shadow texture binding, and allow declared block-entity shadow vertex storage-image writes.
- Route sign text through Iris BlockTrans with the matching vertex stage and alpha cutoff. A matched generated Sildur sign fixture on a prerelease candidate measured glyph-mask IoU 0.9333; it was not repeated on the final candidate. A separate fresh c341 scene showed a blank sign on both Iris and Veyra, so it does not establish readability in that scene.
- Drain deferred Vulkan image frees before allocator destruction; validate resize, dimension changes, pack disable/re-enable and resource reload.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Put `veyra-0.2.0+mc1.21.11.jar` beside VulkanMod in the instance mods folder. Remove older Veyra JARs and keep the sources JAR out of the instance.
- Put shader-pack folders or ZIPs in `shaderpacks`. Press F6 to select a pack; use Configure / Apply for its supported options and profiles.

## Validation and limits

- Exact merged-main JDK 21 gate at `c242eb8`: 607 tests, 0 failures/errors/skips; 104 example shaders and 122 direct Vulkan SPIR-V modules; build, GraphicsSmoke compilation, and example-pack packaging passed. Two existing Mixin mapping warnings remain in `GameRendererMixin`.
- Pinned source audit at `c242eb8`: four packs, three dimensions, 12 defaults and 63 profile cases; 939 geometry fragments accepted, none rejected; all 18 active compute stages compiled. This is source-only evidence.
- The PR #145 code tree (head `b1fff75`, now merged as `c242eb8`) passed all 30 hosted Lavapipe Graphics jobs. RTX 3070 lifecycle runs passed for Complementary Reimagined HIGH (360,000 pixels / 134 buckets), BSL HIGH (360,000 / 126), Sildur default (360,000 / 160), and Unbound COMPLEMENTARY (360,000 / 122), including resize, dimension round-trip, disable/re-enable and resource reload.
- A matched Complementary HIGH direct Fresnel probe matched Iris in an 8,700-pixel ROI. The water basin ROI RGB MAE is 13.87 against Iris, but a same-main old-matrix baseline did not render because VulkanMod rejected generated `copy_*` / `depthtex1_capture` shaders. This is not a before/after claim; water color and broad visual parity remain incomplete.
- A matched Complementary shadow fixture on the earlier shadow-fix candidate showed a natural golem cast shadow (ROI luminance MAE 5.33 vs Iris, down from 22.67) and the strengthened depth/color-alias fixture passed 8,788 active / 0 disabled pixels. Generated-scene evidence does not certify natural worlds.
- The release workflow gates publication on the pinned source audit, full Lavapipe graphics matrix, build/tests, packaging and distributable verification.
- Geometry/tessellation stages, broader interfaces, arbitrary pack resources and sampler semantics remain outside this documented subset and may produce a clear load error. Selected profiles and generated scenes do not certify complete packs, natural worlds, all option combinations or full Iris parity.
- A historical Windows shutdown once ended with 0xC0000409, and one modified-water diagnostic later faulted in VulkanMod vkDestroyImageView. Each was followed by a normal control/rerun; causes are unknown. The latter involved a temporary shader diagnostic and does not establish a stock-pack shutdown failure.
