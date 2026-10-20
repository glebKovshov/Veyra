# Veyra 0.2.0-beta.14+mc1.21.11 - experimental prerelease

This experimental prerelease extends Veyra's native Vulkan multiple-render-target path to selected entity geometry. It does not claim full Iris parity, stable-release readiness, or correct rendering of complete third-party shaderpacks.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.14+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.13

- Add native multiple render targets for supported entity, first-person hand, block-entity, and sign-text fragments, preserving explicit GLSL locations and legacy DRAWBUFFERS/RENDERTARGETS mappings.
- Route entity-family pipeline layouts and blend settings through the existing Vulkan output path, allocate referenced scene-sized targets, and unregister retired pipeline metadata.
- Preserve entity-specific specular texture binding; terrain atlas binding now applies only to terrain and shadow pipelines.
- Work around VulkanMod 0.6.8's layout-token parser by expanding output declarations from one-line macros in the GLSL compiler.
- Add unit, shaderc, and Lavapipe `entity-mrt` readback coverage.

## Validation

- On checked code head 38a4089504cdedbc2be9ba0964aa6991394cd5cb, Build run [39373977134](https://github.com/glebKovshov/Veyra/actions/runs/39373977134) passed `build validateShaders` and distributable verification. The uploaded JUnit reports contain 220 tests, 0 failures, 0 errors, and 0 skipped; 48 example shaders were processed and 45 compiled directly to Vulkan SPIR-V.
- Graphics smoke run [39373977059](https://github.com/glebKovshov/Veyra/actions/runs/39373977059) passed all 17 Lavapipe fixtures. The entity MRT fixture read back 18,532 active magenta pixels through `colortex1`; specular-entity read back 18,293. Runtime Vulkan validation errors were 0. Process teardown still emitted 10,035 validation diagnostics.
- Pinned source audit run [39373977114](https://github.com/glebKovshov/Veyra/actions/runs/39373977114) passed across 4 pinned packs, 12 default cases, 63 declared-profile/dimension cases, and 5,178 preprocessed stages: 486 geometry fragments accepted and 231 rejected. Every record has `visualValidation=false`.
- The tag release workflow repeats source audit, graphics smoke, build/tests, and distributable verification before publishing. Synthetic fixtures do not establish third-party pack appearance or physical-GPU compatibility.

## Known limits

- Complementary Reimagined and Unbound accept 7/10 default geometry fragments in each tested dimension. Translucent block/entity inputs, water `viewVector`, and active `shadowcomp.csh` compute programs in selected profiles remain unsupported.
- BSL 10.1.8 accepts 7/9 default geometry fragments in each tested dimension. Its water `mat` input and active cloud `texture` sampler remain unsupported.
- Sildur's Vibrant 2.02 Extreme accepts 0/5 default geometry fragments; `colortex4` inputs, attachment feedback, and cloud inputs remain unsupported.
- General entity-family output compatibility, other geometry MRT, compute/tessellation, physical-GPU testing, third-party-pack visual validation, and full Iris parity remain incomplete.

Unsupported active features are diagnosed instead of silently filled with invented values. See `COMPATIBILITY.md` and `VALIDATION.md` for source-audit details and verification scope.
