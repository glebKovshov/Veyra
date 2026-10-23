# Veyra 0.2.0-beta.33+mc1.21.11 - experimental prerelease

Beta.33 adds static Iris `normals` sampling for terrain and shadow block atlases. Resource-pack `_n.png` sprites are packed into the live albedo atlas, missing sprites receive a flat tangent-space normal, and vector-aware resampling keeps generated mip levels normalized. This release remains an experimental compatibility subset: entity normal maps and animated normal/specular maps are unsupported, and no whole-pack Iris parity is claimed.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.33+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Changes since beta.32

- Load static terrain and shadow block-atlas normal maps from resource-pack `_n.png` sprites and expose the normal sampler to supported terrain/shadow fragments.
- Use the flat normal `(127, 127, 255, 255)` when a sprite has no normal map; resample vectors and renormalize every generated mip level.
- Reserve descriptor image slot 6 for the normal atlas, shift auxiliary geometry images, and invalidate cached atlas materials when resources reload or pipelines shut down.
- Add a `normal-atlas` example and graphics-smoke fixture that checks authored normal-map sampling at mip level 2.

## Validation and limits

The local JDK 21 gate passed **538 tests, 0 failures/errors/skips**; `validateShaders` processed 99 examples and compiled 115 shader stages to Vulkan SPIR-V. `build`, `packageExamplePacks`, and `compileGraphicsSmokeJava` passed. On an NVIDIA RTX 3070, the `normal-atlas` fixture passed with **223,177 active magenta pixels** while sampling authored `_n.png` data at mip level 2. PR #90 Build, the pinned Shaderpack source audit, and all **46/46** hosted Graphics jobs passed on the exact feature head. The release workflow runs these gates again for the release candidate and verifies the published distributables.

This support is limited to static terrain and shadow block-atlas maps. Entity normal maps and animated normal/specular maps remain unsupported. The focused GPU fixture and hosted smoke suite do not certify full shaderpack visuals, full Iris parity, or every lifecycle path. See [COMPATIBILITY.md](COMPATIBILITY.md) for the measured support boundaries.
