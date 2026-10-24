# Veyra 0.2.0-beta.41+mc1.21.11 - experimental prerelease

Beta.41 brings Sildur's Vibrant particle and block-entity shaders online by matching how Iris feeds vertex attributes that Minecraft's formats don't carry. It keeps every beta.40 particle and sign-text fix.

## Changes

- **`mc_Entity` defaults:** Iris' entity vertex format has no `mc_Entity` element, so a pack's `attribute vec4 mc_Entity` stays unbound and reads OpenGL's default `(0, 0, 0, 1)`. Veyra now supplies that value to entity and block-entity programs and to particle vertex stages, instead of skipping them.
- **Particle vertex stages** accept a pack's own `vec4 normal` output.
- **Particle fragments** keep `entityColor` at zero, as Iris does, instead of reading an entity-only input.
- **Robustness:** if a particle fallback's vertex stage fails to compile, Veyra retries fragment-only rather than disabling all particle shaders.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.41+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Validation and known limits

RTX 3070 lifecycle runs (resize, Nether and return, disable/re-enable, resource reload), all passed with exit code 0:

| Pack | Non-black px | Color buckets | Skipped overrides |
|---|---|---|---|
| Sildur's Vibrant (default) | 360,000 | 58 | sign text only |
| Complementary Reimagined r5.9.3 | 331,449 | 58 | none |
| Complementary Unbound r5.9.3 | 359,938 | 60 | none |
| BSL HIGH | 360,000 | 41 | none |

The local JDK 21 gate passed 580 tests without failures, errors or skips. PR #110 passed all 57 hosted checks before merging; the release workflow repeats the pinned source audit, full Lavapipe matrix, versioned build/tests and distributable verification.

Sildur's sign text is still skipped: its vertex stage stores the lightmap in `texcoord.zw` and material flags in `normal.a`, so it can't be emulated fragment-only. These are generated-scene lifecycle checks, not natural-world visual certification. The one-off Windows shutdown exit (0xC0000409) from an earlier beta has not recurred, but its cause is unproven.

Veyra remains an experimental Iris-compatible subset and does not claim stable Iris parity. See COMPATIBILITY.md and ARCHITECTURE.md for the measured scope.
