# Veyra 0.2.0-beta.16+mc1.21.11 - experimental prerelease

This experimental prerelease adds Iris' terrain `absMidCoordPos` varying to Veyra's VulkanMod terrain bridge. It does not claim full Iris parity, stable-release readiness, or correct rendering of complete third-party shaderpacks.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.16+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.15

- Expose flat Iris `absMidCoordPos`, calculated from the existing per-quad midpoint UV and each terrain vertex UV. The compressed terrain vertex format and stride are unchanged.
- Add an adapter regression, a shaderc fixture, and a Lavapipe readback assertion covering the absolute midpoint signal.

## Validation

- Build [#458](https://github.com/glebKovshov/Veyra/actions/runs/39385648498) on PR #31's final head passed with 227 tests, 0 failures, 0 errors, and 0 skipped; `validateShaders` processed 50 examples and compiled 47 directly to Vulkan SPIR-V. JAR verification passed for the pre-release metadata.
- [Graphics smoke #174](https://github.com/glebKovshov/Veyra/actions/runs/39385648591) passed all 19 Lavapipe fixtures. The midpoint fixture reported 56,892 active magenta pixels, 165,648 opposite-region pixels, 0 disabled magenta pixels, and 0 runtime validation errors; teardown reported 10,031 diagnostics. [Source audit #135](https://github.com/glebKovshov/Veyra/actions/runs/39385648634) accepted 486 / rejected 231 geometry fragments across 75 cases, 4 pinned packs, and 5,178 stages; every case has `visualValidation=false`.
- Release CI rebuilds and verifies the final versioned artifact. Synthetic fixtures do not establish third-party-pack appearance or physical-GPU compatibility.

## Known limits

- Complementary Reimagined and Unbound accept 7/10 default geometry fragments in each tested dimension. Terrain `midCoord`, translucent block/entity inputs, and selected `shadowcomp.csh` compute profiles remain unsupported.
- BSL 10.1.8 accepts 6/9 default geometry fragments in each tested dimension. Terrain `recolor`, water `dist`, and the active cloud `texture` sampler remain unsupported.
- Sildur's Vibrant 2.02 Extreme accepts 0/5 default geometry fragments; `colortex4` inputs, attachment feedback, and cloud inputs remain unsupported.
- The Lavapipe shutdown still reports validation diagnostics. Physical-GPU testing, third-party-pack visual validation, and full Iris parity remain incomplete.

Unsupported active features are diagnosed instead of silently filled with invented values. See `COMPATIBILITY.md` and `VALIDATION.md` for scope and measured results.
