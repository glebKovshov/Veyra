# Veyra 0.2.1+mc1.21.11

Patch release for Minecraft 1.21.11 and VulkanMod 0.6.8.

## Changes

- Remove repeated GLSL identifier scans from entity submissions. Analyze usage at pipeline preparation, skip unused identifier variants, and cache shadow-program presence for each pack configuration.
- Restore sharp magnification of block textures with shader packs while retaining mipmaps and anisotropic minification. Custom block-atlas aliases use Minecraft's sampler.
- Apply alpha testing after terrain and entity shader-pack fragment programs, including early returns, so transparent parts of vegetation and entity textures do not write opaque color and depth.
- Separate camera bob/hurt effects from projection for shader-pack depth reconstruction, reflections and shadows. Keep entity vertex and normal transforms consistent without applying camera effects twice.

## Installation

Requires Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.

Put `veyra-0.2.1+mc1.21.11.jar` in the instance's `mods` folder beside VulkanMod and remove older Veyra JARs. The sources JAR is for development. Press F6 to select and configure a shader pack.

## Validation and limits

- Local JDK 21 checks: 617 tests passed; 104 example shaders processed and 122 direct Vulkan SPIR-V modules compiled. The BSL v10.1.8 default source audit passed with no capability or adapter errors.
- The user tested development builds in their Modrinth profile and reported that freezes were gone and block textures were sharper. These reports are not a benchmark or a full-pack compatibility certification.
- Alpha cutoff and camera-matrix changes have CPU regression tests and shader compilation coverage. The intermittent black-screen report has not been reproduced under a controlled graphics test; its resolution is not confirmed.
- Publication is gated by the repository's pinned shader-pack source audit, Lavapipe graphics matrix, build/tests, example packaging and distributable verification.
- Existing shader-pack compatibility limits remain: see COMPATIBILITY.md. Full Iris visual parity, all profiles, and natural-world rendering are not certified.
