# Veyra 0.2.0-beta.39+mc1.21.11 - experimental prerelease

Beta.39 closes two Complementary Reimagined runtime gaps that beta.38 skipped with warnings:

- Textured particle fallbacks (`gbuffers_textured`/`gbuffers_textured_lit` used for particles when a pack has no `gbuffers_particles`) now bridge Iris samplers, including `gaux2`/`colortexN`, noise, specular, shadow and lightmap, through the existing particle pipeline binding.
- Sign-text fragments now receive `upVec`, `sunVec` and `eastVec`, derived from `gbufferModelView` and `sunPosition` as in the entity bridge.

All beta.38 functionality is preserved.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.39+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Validation and known limits

The local JDK 21 gate passed 575 tests without failures, errors or skips, plus build and graphics-smoke compilation. PR #106 passed Build, source audit and all hosted Lavapipe Graphics jobs (57 checks) before merging. The release workflow repeats the pinned source audit, full Lavapipe graphics matrix, versioned build/tests and distributable verification before publication.

The two new routes are covered by adapter unit tests and hosted compilation; they have not yet been visually confirmed in a natural Complementary world. Whole-pack and natural-world visuals remain uncertified, and the earlier one-off Windows native-shutdown exit (0xC0000409) remains unexplained.

Veyra remains an experimental Iris-compatible subset; it does not establish stable Iris parity. See COMPATIBILITY.md and ARCHITECTURE.md for the measured scope.
