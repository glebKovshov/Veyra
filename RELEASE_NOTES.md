# Veyra 0.2.3+mc1.21.11

Patch release for Minecraft 1.21.11 and VulkanMod 0.6.8.

## Changes

- Fix mipmapped post-pass sampler bindings so Minecraft resolves colortex snapshots to the expected shader-pack samplers. This restores normal rendering when Complementary Reimagined HIGH requests colortex mipmaps for exposure and reflections.
- Retain the 0.2.2 fixes for GUI/inventory pipelines and shader-resource initialization.

## Installation

Requires Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.

Put `veyra-0.2.3+mc1.21.11.jar` in the instance's `mods` folder beside VulkanMod and remove older Veyra JARs. The sources JAR is for development. Press F6 to select and configure a shader pack.

## Validation and limits

- Local RTX 3070 Complementary Reimagined r5.9.3 HIGH lifecycle passed: 360,000 non-black pixels / 137 color buckets, including resize, dimension changes, disable/re-enable and reload.
- The versioned v0.2.3 candidate passed the local Temurin JDK 21.0.13 gate: 622 tests, shader validation, example packaging, GraphicsSmoke compilation and JAR verification. GitHub's release workflow repeats the checks on the published ref.
- Publication is gated by the pinned shader-pack source audit, Lavapipe graphics matrix, build/tests, shader compilation and distributable verification.
- Full Iris visual parity is not certified; see COMPATIBILITY.md for the measured limits.
