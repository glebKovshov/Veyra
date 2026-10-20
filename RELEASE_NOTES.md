# Veyra 0.2.0-beta.13+mc1.21.11 - experimental prerelease

This experimental prerelease adds limited Iris first-person hand compatibility. It does not claim full Iris parity, stable-release readiness, or that third-party shaderpacks render correctly.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.13+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.12

- Accept Iris `varying float isMainHand` in first-person hand programs and select cached variants for the logical main hand (`1.0`) and offhand (`0.0`).
- Split comma-separated legacy varying declarations and bridge BSL `sunVec` to Iris `sunPosition`, with `upVec` and `eastVec` derived from the current `gbufferModelView` axes.
- Refresh the pinned source audit after these changes. BSL's default hand, entity, and block fragment programs now pass source adaptation; other BSL blockers remain.

## Validation

- For checked code head `092c611e5a445f3fd958d7db22f5889e2622d9f0`, GitHub Build run [39368301651](https://github.com/glebKovshov/Veyra/actions/runs/39368301651) passed `build validateShaders`: 214 JUnit tests, 0 failures, 0 errors, and 0 skipped; 47 example shaders preprocessed and 44 compiled directly to Vulkan SPIR-V. The BSL-style hand direction fixture compiled to SPIR-V.
- Graphics smoke run [39368301683](https://github.com/glebKovshov/Veyra/actions/runs/39368301683) passed all 16 Lavapipe fixtures. The synthetic hand/item fixture rendered 32,321 main-hand magenta pixels for item ID 42 and 14,102 offhand green pixels for item ID 43; both signals were 0 with shaders disabled. Runtime Vulkan validation errors were 0. Process shutdown still emitted 10,036 device-destruction diagnostics.
- Pinned source audit run [39368301649](https://github.com/glebKovshov/Veyra/actions/runs/39368301649) passed across 4 packs, 12 default cases, 63 declared-profile/dimension cases, and 5,178 preprocessed stages: 234 geometry fragments accepted and 483 rejected. Every record has `visualValidation=false`.
- The tag release workflow reruns the pinned audit, graphics suite, build/tests, and distributable verifier before publication. Synthetic fixtures do not establish third-party pack appearance or physical-GPU compatibility.

## Known limits

- Complementary Reimagined and Unbound still need outputs beyond color attachment 0 in block/entity/hand programs, unsupported inputs for translucent programs, `viewVector` in water, and compute execution in selected profiles.
- BSL 10.1.8 accepts 5/9 default geometry fragments in each tested dimension. Its water `mat`, active cloud `texture` sampler, and glowing/translucent entity outputs beyond attachment 0 remain unsupported.
- Sildur's still requires `colortex4` inputs, attachment-feedback behavior, and cloud inputs VulkanMod does not provide.
- Iris armor-trim and cape IDs, general non-terrain MRT, compute/tessellation, physical-GPU validation, third-party-pack visual validation, and full Iris parity remain incomplete.

Unsupported active features are diagnosed rather than rendered with invented values. See `COMPATIBILITY.md` and `VALIDATION.md` for the pinned source results and current scope.
