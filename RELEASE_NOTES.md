# Veyra 0.2.0-beta.17+mc1.21.11 - experimental prerelease

This experimental prerelease adds Iris' terrain `midCoord` varying to Veyra's VulkanMod terrain bridge and initializes an unset VulkanMod texture matrix to identity. It does not claim full Iris parity, stable-release readiness, or correct rendering of complete third-party shaderpacks.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.17+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.16

- Expose flat Iris `midCoord` on terrain after applying VulkanMod's current texture matrix to the vertex UV and compressed per-quad midpoint. Derive `signMidCoordPos` and `absMidCoordPos` from that same transformed pair.
- Initialize VulkanMod's texture-matrix mapped buffer to identity when its values are unset or non-finite; preserve valid transforms. Add a direct-buffer regression for initialization and transform preservation.
- Extend the terrain-midpoint shaderc and Lavapipe fixture to require the new input. PR #34 passed all 19 Lavapipe fixtures after this fix.

## Validation

- PR Build [39393899981](https://github.com/glebKovshov/Veyra/actions/runs/39393899981) passed `build validateShaders` and distributable verification; local forced JDK 21 `build validateShaders --rerun-tasks` passed 229 tests, 0 failures/errors/skips, and processed 50 examples / 47 direct SPIR-V compilations. [Graphics smoke 39393900000](https://github.com/glebKovshov/Veyra/actions/runs/39393900000) passed all 19 Lavapipe fixtures. The corrected terrain-midpoint fixture reported 55,237 active magenta pixels, 167,494 opposite-region pixels, 0 disabled magenta pixels, and 0 runtime validation errors. [Source audit 39393899973](https://github.com/glebKovshov/Veyra/actions/runs/39393899973) accepted 528 / rejected 189 geometry fragments across 75 cases, 4 pinned packs, and 5,178 stages; every case has `visualValidation=false`.
- Release CI rebuilds and verifies the final versioned artifact. Synthetic fixtures do not establish third-party-pack appearance or physical-GPU compatibility.

## Known limits

- Complementary Reimagined and Unbound accept 8/10 default geometry fragments in each tested dimension. Translucent block/entity inputs and selected `shadowcomp.csh` compute profiles remain unsupported.
- BSL 10.1.8 accepts 6/9 default geometry fragments in each tested dimension. Terrain `recolor`, water `dist`, and the active cloud `texture` sampler remain unsupported.
- Sildur's Vibrant 2.02 Extreme accepts 0/5 default geometry fragments; `colortex4` inputs, attachment feedback, and cloud inputs remain unsupported.
- The Lavapipe shutdown still reports validation diagnostics. Physical-GPU testing, third-party-pack visual validation, and full Iris parity remain incomplete.

Unsupported active features are diagnosed instead of silently filled with invented values. See `COMPATIBILITY.md` and `VALIDATION.md` for scope and measured results.
