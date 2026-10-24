# Veyra 0.2.0-beta.40+mc1.21.11 - experimental prerelease

Beta.40 is a compatibility batch driven by full lifecycle runs of four real shaderpacks on an RTX 3070. It closes every skipped particle and sign-text override those runs reported in Complementary Reimagined, Complementary Unbound and BSL.

## Changes

- **Particle fallbacks:** when a pack has no `gbuffers_particles`, Veyra now runs the pack's `gbuffers_textured[_lit]` vertex stage as well as its fragment (the same vertex adaptation `gbuffers_particles` uses), so pack varyings such as `lmCoord` reach the fragment. If the vertex stage can't be adapted, Veyra retries fragment-only instead of skipping. Iris samplers (`gaux2`/`colortexN`, noise, specular, shadow, lightmap) are bridged on this path (from beta.39).
- **Sign text:** now supplies `upVec`, `sunVec`, `eastVec`, `northVec`, legacy `color`, and a face `normal` derived per pixel from `viewPos` (text vertices carry no normal). See-through text, whose vertex format has no UV2, gets Minecraft's full-bright `lmcoord` instead of being skipped. Lightmap inputs are now detected in comma-separated declarations such as BSL's `varying vec2 texCoord, lmCoord;`.
- **Debugging:** adapted sign-text fragments are included in `veyra.debug.dumpShaderSources` output.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.40+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Validation and known limits

RTX 3070 lifecycle runs (resize, Nether and return, disable/re-enable, resource reload), all passed with exit code 0:

| Pack | Non-black px | Color buckets | Skipped overrides |
|---|---|---|---|
| Complementary Reimagined r5.9.3 | 328,768 | 53 | none |
| Complementary Unbound r5.9.3 | 359,671 | 63 | none |
| BSL HIGH | 360,000 | 43 | none |
| Sildur's Vibrant (default) | 360,000 | 46 | block-entity fallbacks reading `mc_Entity` |

The local JDK 21 gate passed 580 tests without failures, errors or skips. PR #108 passed all 57 hosted checks (Build, source audit and the Lavapipe Graphics matrix) before merging. The release workflow repeats the pinned source audit, the full Lavapipe matrix, versioned build/tests and distributable verification before publishing.

These are generated-scene lifecycle checks, not visual certification of whole packs in natural worlds. Sildur block-entity fallbacks that read `mc_Entity` are still skipped, because the value Iris supplies there is unverified. A one-off Windows native-shutdown exit (0xC0000409) from an earlier beta has not recurred in about 13 runs today, but its cause is unproven.

Veyra remains an experimental Iris-compatible subset and does not claim stable Iris parity. See COMPATIBILITY.md and ARCHITECTURE.md for the measured scope.
