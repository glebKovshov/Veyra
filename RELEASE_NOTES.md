# Veyra 0.2.0-beta.18+mc1.21.11 - experimental prerelease

This experimental prerelease bridges supported legacy Iris fog members to Minecraft's live Fog UBO. It does not claim full Iris parity, stable-release readiness, or correct rendering of complete third-party shaderpacks.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.18+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.17

- Rewrite legacy `gl_Fog.color`, `gl_Fog.start`, and `gl_Fog.end` to Minecraft's Fog UBO fields; derive `gl_Fog.scale` from the same environmental start/end values.
- Keep `gl_Fog.density` explicitly unsupported because Veyra has no validated runtime density value.
- Extend the geometry-fog example and regression coverage. PR #37 passed Build, all 19 Lavapipe fixtures, and the 75-case pinned shaderpack source audit.

## Validation

- On PR head `492f0d307427db4385c4dc5e154230aaac49e34d`, Build [39399069930](https://github.com/glebKovshov/Veyra/actions/runs/39399069930), Graphics smoke [39399069988](https://github.com/glebKovshov/Veyra/actions/runs/39399069988), and source audit [39399069951](https://github.com/glebKovshov/Veyra/actions/runs/39399069951) passed. Local forced JDK 21 `build validateShaders packageExamplePacks --rerun-tasks` passed 231 tests (0 failures/errors/skips), processing 50 examples and compiling 47 directly to Vulkan SPIR-V.
- The pinned audit covers 75 cases, 4 pinned packs, and 5,178 stages: 636 geometry fragments accepted / 81 rejected; every record has `visualValidation=false`. Lavapipe teardown still reports validation diagnostics.
- Release CI rebuilds and verifies the final versioned artifact. Synthetic fixtures do not establish third-party-pack appearance or physical-GPU compatibility.

## Known limits

- Complementary Reimagined and Unbound accept 10/10 default geometry fragments in each tested dimension. Selected profiles still require `spriteBounds` and the unsupported `shadowcomp.csh` compute stage.
- BSL 10.1.8 accepts 6/9 default geometry fragments in each tested dimension. Terrain `recolor`, water `dist`, and the active cloud `texture` sampler remain unsupported.
- Sildur's Vibrant 2.02 Extreme accepts 0/5 default geometry fragments; `colortex4` inputs, attachment feedback, and cloud inputs remain unsupported.
- No third-party shaderpack visual validation or physical-GPU testing has been completed; full Iris parity and clean Vulkan teardown remain incomplete.

Unsupported active features are diagnosed instead of silently filled with invented values. See `COMPATIBILITY.md` and `VALIDATION.md` for scope and measured results.
