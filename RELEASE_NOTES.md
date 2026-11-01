# Veyra 0.2.2+mc1.21.11

Patch release for Minecraft 1.21.11 and VulkanMod 0.6.8.

## Changes

- Honor per-pass colortex mipmap requests, generating and sampling mip chains for shader-pack exposure and reflections.
- Keep GUI and inventory item rendering on vanilla pipelines instead of world shader-pack pipelines.
- Prepare restored shader-pack textures before world rendering, avoiding uploads inside atlas render passes.
- Allocate and clear geometry color buffers before waiting for the first completed shadow map.

## Installation

Requires Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.

Put `veyra-0.2.2+mc1.21.11.jar` in the instance's `mods` folder beside VulkanMod and remove older Veyra JARs. The sources JAR is for development. Press F6 to select and configure a shader pack.

## Validation and limits

- Local development build: 622 tests passed; distributable contents verified. Final release checks are recorded by GitHub Actions.
- Publication is gated by the pinned shader-pack source audit, Lavapipe graphics matrix, build/tests, shader compilation and distributable verification.
- The fixes address reported startup and first-world-frame exceptions. The final candidate has not been launched locally; sunlight/moonlight exposure and inventory appearance still need visual confirmation.
- Existing compatibility limits remain: see COMPATIBILITY.md. Full Iris visual parity is not certified.
