# Veyra 1.2.0

Veyra 1.2.0 runs on every Minecraft 1.21.x release that has a VulkanMod build: 1.21, 1.21.1 to 1.21.5, and 1.21.9 to 1.21.11. Each version gets its own JAR, built and tested against the exact Minecraft and VulkanMod versions it supports. Shader-pack behavior on 1.21.11 is unchanged from 1.1.0.

## Supported versions

| Minecraft | VulkanMod | Veyra JAR |
| --- | --- | --- |
| 1.21.11 | 0.6.8 | `veyra-1.2.0+mc1.21.11.jar` |
| 1.21.10 | 0.6.6 | `veyra-1.2.0+mc1.21.10.jar` |
| 1.21.9 | 0.6.6 | `veyra-1.2.0+mc1.21.9.jar` |
| 1.21.5 | 0.5.6 | `veyra-1.2.0+mc1.21.5.jar` |
| 1.21.4 | 0.5.6 | `veyra-1.2.0+mc1.21.4.jar` |
| 1.21.3 | 0.5.3 | `veyra-1.2.0+mc1.21.3.jar` |
| 1.21.2 | 0.5.3 | `veyra-1.2.0+mc1.21.2.jar` |
| 1.21.1 | 0.6.7+1.21.1 | `veyra-1.2.0+mc1.21.1.jar` |
| 1.21 | 0.6.7+1.21.1 | `veyra-1.2.0+mc1.21.jar` |

1.21.6–1.21.8 are not supported because VulkanMod has no release for them.

## Changes since 1.1.0

- **1.21.9 and 1.21.10** use VulkanMod 0.6.6, the same renderer generation as 1.21.11.
- **1.21.5 and 1.21.4** use VulkanMod 0.5.6. 1.21.4 predates Minecraft's GPU device API, so a compatibility backend provides it.
- **1.21.3 and 1.21.2** use VulkanMod 0.5.3. Veyra follows its older class names and its terrain vertex layout. The sky is drawn by core programs rather than render types, and items are drawn without render states; Veyra handles both.
- **1.21.1 and 1.21** use VulkanMod 0.6.7+1.21.1. Veyra reads core shaders and their configs directly, since this Minecraft has no shader manager. It compiles pipelines with VulkanMod 0.6.7's GLSL parser, keeping multiple fragment outputs and their locations. The frame graph and post passes of later versions are emulated.
- **OpenGL emulation fixes on older VulkanMod.**
  - A clear with no framebuffer bound reaches the main target.
  - Post passes apply their pipeline's fixed-function state.
  - Render-target creation and resizing restore the previous render pass.
  - Sampled depth-stencil images use depth-only views.
- **Releases and CI** build and verify every target. Each release attaches one JAR per Minecraft version.

## Installation

Install Fabric Loader 0.18.4 or newer, Java 21 or newer, and the VulkanMod version from the table for your Minecraft version. Download the Veyra JAR whose `+mc` suffix matches your Minecraft version and put it in `mods/` beside VulkanMod. Remove older Veyra JARs. Open **Options → Video Settings → Shader Settings**, or press F6.

Assets: one install JAR per Minecraft version, the 1.21.11 sources JAR, an example-packs ZIP and `SHA256SUMS.txt`. Veyra does not redistribute or modify shader pack archives.

## Validation

- **Every target:** builds, passes 706–707 JUnit tests and shader compilation checks, and passes JAR metadata verification.
- **On an RTX 3070** with Vulkan core and synchronization validation, every target from 1.21 to 1.21.5 runs eight graphics fixtures with zero diagnostics: vanilla baseline, entity MRT, begin-stage fragments, HDR compute targets, compute SSBOs, cloud model-view, animated normal/specular atlases, and water with separate shadow depth. The same seven shader fixtures also pass on 1.21.9 and 1.21.10.
- **1.21.11 hosted gates** are unchanged: the Lavapipe graphics matrix, the pinned source audit, the build and shader compilation.

## Known limits

- Before 1.21.6, the shader-pack sky stages (`gbuffers_skybasic`, `gbuffers_skytextured`) are not applied. Vanilla sky rendering is unaffected.
- Weather and particle stages were not validated before 1.21.9.
- Each older version is tested only against its pinned VulkanMod release.
- The 1.1.0 limits still apply: pixel parity with Iris is not certified, packs compile on the render thread, up to 16 logical color buffers and 8 simultaneous outputs are supported, and geometry/tessellation stages are not supported.

See `docs/MULTIVERSION.md` and `COMPATIBILITY.md` for details.
