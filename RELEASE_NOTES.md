# Veyra 0.2.0-beta.19+mc1.21.11 - experimental prerelease

This experimental prerelease extends terrain and water inputs used by BSL and related Iris-compatible packs. It does not claim full Iris parity, stable-release readiness, or correct rendering of complete third-party shaderpacks.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.19+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.18

- Bridge terrain `dist` from view-space position and BSL `recolor` from the mapped terrain block ID.
- Supply water direction vectors and BSL advanced-material UVs from Iris uniforms, the live texture matrix, and per-quad terrain midpoint.
- PR #40 passed Build, all 19 Lavapipe fixtures, and the 75-case pinned shaderpack source audit. The final versioned build and release checks run on the beta.19 tag.

## Validation

- On PR #40's final head `f2c3a7d478210372a6af686e00453c406d1b9fae`, Build [39405808357](https://github.com/glebKovshov/Veyra/actions/runs/39405808357), Graphics smoke [39405808241](https://github.com/glebKovshov/Veyra/actions/runs/39405808241), and source audit [39405808038](https://github.com/glebKovshov/Veyra/actions/runs/39405808038) passed. The merged `main` build also passed locally with JDK 21: 234 tests, 50 examples processed, and 47 direct Vulkan SPIR-V compilations.
- The pinned audit covers 75 cases, 4 pinned packs, and 5,178 stages: 672 geometry fragments accepted / 45 rejected; every record has `visualValidation=false`. Lavapipe teardown still reports validation diagnostics.
- Release CI rebuilds and verifies the final versioned artifact. Synthetic fixtures do not establish third-party-pack appearance or physical-GPU compatibility.

## Known limits

- Complementary Reimagined and Unbound accept 10/10 default geometry fragments in each tested dimension. Selected profiles still require `spriteBounds` and the unsupported `shadowcomp.csh` compute stage.
- BSL 10.1.8 accepts 8/9 default geometry fragments in each tested dimension. The active cloud `texture` sampler remains unsupported.
- Sildur's Vibrant 2.02 Extreme accepts 0/5 default geometry fragments; `colortex4` inputs, attachment feedback, and cloud inputs remain unsupported.
- No third-party shaderpack visual validation or physical-GPU testing has been completed; full Iris parity and clean Vulkan teardown remain incomplete.

Unsupported active features are diagnosed instead of silently filled with invented values. See `COMPATIBILITY.md` and `VALIDATION.md` for scope and measured results.
