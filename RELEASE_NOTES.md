# Veyra 0.2.0-beta.45+mc1.21.11 - experimental prerelease

Beta.45 fixes shader-pack `gaux2` handling in sign text pipelines. The text fragment adapter maps it to Veyra's geometry color sampler, and the sign pipeline now receives the matching colortex descriptor.

## Changes

- Bind `gaux2` to colortex5 for sign text geometry inputs.
- Keep shader-pack custom texture aliases ahead of geometry color sampler aliases.
- Add regression coverage for `gaux2` and custom texture alias collisions.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install veyra-0.2.0-beta.45+mc1.21.11.jar beside VulkanMod. Keep only one Veyra JAR in the instance; do not install the sources JAR.
- Put shaderpack folders or ZIPs in shaderpacks. Press F6 to select a pack, then Configure / Apply to edit supported options and profiles.

## Validation and known limits

The JDK 21 gate passed 585 tests without failures, errors or skips; it validated 104 example shaders and compiled 122 direct Vulkan SPIR-V stages. Shader validation, build, graphics-smoke compilation and example-pack packaging passed.

The pinned source audit covered four shaderpacks, 12 defaults and 63 declared-profile cases across three dimensions. It accepted 939 geometry fragments and all 18 active compute stages, with zero rejected geometry fragments. Each row remains source-only with `visualValidation=false`.

The RTX 3070 Complementary HIGH lifecycle smoke passed resize, Nether/Overworld transitions, disable/re-enable and resource reload. A separate synthetic sign-text smoke confirmed the `VeyraGeometryColor5Sampler` descriptor binds colortex5 on `pipeline/text_polygon_offset`. Complementary itself selected `gbuffers_block` for sign text; its lifecycle run does not exercise the `gbuffers_textured` fallback.

Full Iris visual parity, natural-world comparison and whole-pack certification remain unverified. Veyra remains an experimental Iris-compatible subset, not a stable Iris-parity release. See COMPATIBILITY.md and ARCHITECTURE.md for measured scope and unsupported stages.
