# Veyra 0.2.0-beta.12+mc1.21.11 - experimental prerelease

This experimental prerelease adds limited Iris entity-shader compatibility. It does not claim full Iris parity, stable-release readiness, or that third-party shaderpacks render correctly.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install veyra-0.2.0-beta.12+mc1.21.11.jar in the instance's mods folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into shaderpacks. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under veyra/options/; archives are not modified.

## Changes since beta.11

- Map Iris entityColor to Minecraft's overlayColor varying on entity, block, hand, and supported textured-entity paths. Pipelines without the overlay input use the documented transparent fallback.
- Accept Iris's lmCoord spelling for the entity-family lightmap varying.
- Stop treating local viewPos variables in entity fragment programs as missing fragment inputs.
- Avoid multiline regex backtracking in unsupported-uniform checks and scan sampler references once per shader.

## Validation

- Local JDK 21.0.12.1 build passed: 209 JUnit tests, 0 failures/errors/skips; 47 example shader sources processed and 44 directly compiled to Vulkan SPIR-V. The entity overlay, item-entity fallback, and entity lightmap alias fixtures compiled to SPIR-V.
- The pinned source audit covers 4 packs, 12 defaults, 63 declared profiles, and 5,178 preprocessed stages. It accepts 180 geometry fragment programs and rejects 537. Every audit record has visualValidation=false.
- The release workflow requires the GitHub build, pinned source audit, graphics smoke suite, and distributable verifier to pass. Synthetic graphics fixtures do not establish the appearance of third-party packs or physical-GPU compatibility.

## Known limits

- Complementary Reimagined and Unbound entity, block, and hand programs require writes beyond color attachment 0; translucent programs still use unsupported inputs, water requires viewVector, and selected profiles require compute execution.
- BSL still has unsupported entity/block inputs, hand isMainHand, water mat, and cloud texture sampling.
- Sildur's still requires colortex4 reads, attachment feedback, and unsupported cloud inputs.
- Iris armor-trim and cape IDs, general non-terrain MRT, compute/tessellation, physical-GPU validation, third-party-pack visual validation, and full Iris parity remain incomplete.

Unsupported active features are diagnosed rather than rendered with invented values. See COMPATIBILITY.md and VALIDATION.md for the pinned source results and current scope.
