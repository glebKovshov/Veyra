# Veyra 0.2.0-beta.43+mc1.21.11 - experimental prerelease

Beta.43 fixes shader-pack custom texture bindings for post-processing colortex targets. A Complementary Reimagined HIGH smoke confirms its cloud-water texture reaches the adapted deferred sampler and volumetric clouds are visible in the generated scene.

## Changes

- Resolve shader-pack colortex names to adapted post-process sampler descriptors, including custom texture mappings such as texture.deferred.colortex3.
- Bind shader-pack custom textures after native render-pass inputs are installed, so a later target setup does not replace the custom descriptor.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install veyra-0.2.0-beta.43+mc1.21.11.jar beside VulkanMod. Keep only one Veyra JAR in the instance; do not install the sources JAR.
- Put shaderpack folders or ZIPs in shaderpacks. Press F6 to select a pack, then Configure / Apply to edit supported options and profiles.

## Validation and known limits

The local JDK 21 gate passed 583 tests without failures, errors or skips; it validated 104 example shaders and compiled 122 direct Vulkan SPIR-V stages, and passed shader validation, build, graphics-smoke compilation and example-pack packaging.

The RTX 3070 Complementary HIGH generated-scene smoke passed with 230,400 non-black pixels and 124 color buckets. Runtime evidence confirms VeyraColortex3Sampler was bound to lib/textures/cloud-water.png; volumetric clouds are visible again. The controlled scene still differs from Iris in sky/light colors and sign appearance. This targeted capture does not establish natural-world parity or full-pack certification. The historical Windows shutdown exit 0xC0000409 remains unexplained. A local VulkanMod profile also crashed in Reflex 1.1.2's direct OpenGL query call before Veyra loaded; see COMPATIBILITY.md for scope.

Veyra remains an experimental Iris-compatible subset, not a stable Iris-parity release. See COMPATIBILITY.md and ARCHITECTURE.md for measured scope and unsupported stages.
