# Veyra 0.2.0-beta.30+mc1.21.11 - experimental prerelease

Beta.30 bundles a CloudRenderer crash fix with expanded textured-shader compatibility. It remains an experimental compatibility subset; it does not provide full Iris parity or establish that complete third-party shaderpacks are playable.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.30+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Changes since beta.29

- Fix the CloudRenderer startup crash when Mixin searches for `Tesselator.begin`. The nested Minecraft invocation is now remapped while the enclosing VulkanMod method selector remains unremapped. The published beta.25 and beta.29 JARs were both confirmed to lack the runtime mapping; this release contains it.
- Bind Minecraft's live lightmap as `Sampler2` for supported world-border and sky-textured pipelines.
- Link custom `vec4 normal` outputs in `gbuffers_textured` and ignore unused fragment varying declarations before interface linking.

## Validation and known limits

Feature PR [#80](https://github.com/glebKovshov/Veyra/pull/80) merged into `main` at `a1ade554`. Build [39563491000](https://github.com/glebKovshov/Veyra/actions/runs/39563491000), pinned source audit [39563491016](https://github.com/glebKovshov/Veyra/actions/runs/39563491016), and Graphics smoke [39563491037](https://github.com/glebKovshov/Veyra/actions/runs/39563491037) passed.

The local JDK 21 build passed **334 tests**; 85 example shaders were processed and 98 stages compiled directly to Vulkan SPIR-V. The graphics-smoke Java sources compiled locally; the CI graphics workflow completed successfully.

The pinned audit covers 4 archives, 75 profile/dimension cases, and 5,178 stages: **789 geometry fragments accepted / 0 rejected**. The remaining first capability blockers are custom `gbuffers_weather.vsh` execution (72 profile/dimension records) and Sildur's active `mc_Entity` input in `gbuffers_textured.vsh` (3 records). Every audit record has `visualValidation=false`; no complete third-party shaderpack has been visually validated. See [COMPATIBILITY.md](COMPATIBILITY.md) for measured results.
