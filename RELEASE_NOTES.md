# Veyra 0.2.0-beta.35+mc1.21.11 - experimental prerelease

Beta.35 adds animated resource-pack PBR maps to the existing static material-map paths. Animated `_n.png` normal and `_s.png` specular companions follow terrain and shadow atlas sprites, including the base sprite animation frame and sub-frame. Resource-backed entity `SimpleTexture` maps use a per-material animation clock. Normal interpolation operates in vector space and renormalizes the result; specular channel data is interpolated in step with the albedo animation.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.35+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Validation and known limits

The local JDK 21 gate passed **551 tests** with no failures, errors, or skips; validated **102 example shaders**; compiled **118 shader stages to Vulkan SPIR-V**; and completed build, example-pack packaging, and graphics-smoke compilation. The fresh pinned source-only audit passed 75 profile/dimension records / 5,178 preprocessed stages, with 864 geometry-fragment programs accepted / 0 rejected and all 18 active compute stages compiled. It still recorded 18 Complementary capability diagnostics for writable `voxel_img` in the shadow vertex stage; the audit marks every row `visualValidation: false`. The `normal-atlas-animated` fixture passed on an NVIDIA RTX 3070: both synchronized animation states read back **222,724 magenta / 222,722 green pixels**, with **0 / 0** after shader disable. The pinned Sildur v2.02 Extreme `__DEFAULT__` lifecycle harness passed all six stages (360,000 non-black pixels / 45 color buckets); BSL 10.1.8 `HIGH` passed (360,000 / 42); and selected `COMPLEMENTARY` profiles passed for Reimagined r5.9.3 (337,813 / 57) and Unbound r5.9.3 (359,954 / 60). All runtime runs used the generated flat world; Khronos validation was not enabled. These are source/profile and selected-profile lifecycle checks, not whole-pack visual certification. The exact release candidate must also pass the hosted release workflow and `scripts/verify_jar.py`.

The animation input is capped at four million source pixels per map, 64 MiB of source animation data per texture material, and separate 128 MiB animation caches for normal and specular maps (256 MiB combined). Downloaded player skins are not covered. Veyra remains an experimental Iris-compatible subset: these focused fixtures do not certify a complete third-party shaderpack or stable Iris parity. See `COMPATIBILITY.md` for the measured shaderpack matrix and known blockers.
