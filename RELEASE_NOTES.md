# Veyra 0.2.0-beta.15+mc1.21.11 - experimental prerelease

This experimental prerelease adds mapped Iris terrain block IDs and the Iris fluid marker to Veyra's VulkanMod terrain bridge. It does not claim full Iris parity, stable-release readiness, or correct rendering of complete third-party shaderpacks.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.15+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.14

- Carry the active `block.properties` ID and Iris `mc_Entity` render type through VulkanMod's compressed terrain vertices.
- Expose `mat` as either `int` or `float`, and `mc_Entity` as `vec2`; fluids use `mc_Entity.y == 1.0`, other terrain uses `-1.0`.
- Rebuild terrain sections after selecting a pack when its block mappings change.
- Parse repeated `block.<id>` lines and comma-separated property values, and use current Iris direct block/state precedence over tags.
- Add original `terrain-block-id` and `water-block-id` examples and readback fixtures.

## Validation

- On PR head `85232d104a33bc8b9d8bada3012ef97c0063148b`, [Build run 39381177655](https://github.com/glebKovshov/Veyra/actions/runs/39381177655) passed `test`, `validateShaders`, `build`, and JAR verification. JUnit reports contain 226 tests, 0 failures, 0 errors, and 0 skipped. The post-merge Build run [39381601965](https://github.com/glebKovshov/Veyra/actions/runs/39381601965) also passed.
- [Graphics smoke run 39381177589](https://github.com/glebKovshov/Veyra/actions/runs/39381177589) passed all 19 Lavapipe fixtures. Terrain-ID readback was 223,648 active magenta pixels / 0 disabled; water-ID was 226,902 / 0. Both reported 0 runtime validation errors, with 10,031 and 10,049 teardown diagnostics respectively.
- [Shaderpack source audit 39381177592](https://github.com/glebKovshov/Veyra/actions/runs/39381177592) covered 75 cases across 4 pinned packs and 5,178 stages: 432 geometry fragments accepted / 285 rejected. Every record has `visualValidation=false`.
- The tag Release workflow repeats the source audit, all graphics fixtures, Gradle build/tests, and distributable verification before publishing. Synthetic fixtures do not establish third-party pack appearance or physical-GPU compatibility.

## Known limits

- Complementary Reimagined and Unbound accept 6/10 default geometry fragments in each tested dimension. Terrain and water still require `absMidCoordPos`; translucent block/entity inputs and selected `shadowcomp.csh` compute profiles remain unsupported.
- BSL 10.1.8 accepts 6/9 default geometry fragments in each tested dimension. Terrain `recolor`, water `dist`, and the active cloud `texture` sampler remain unsupported.
- Sildur's Vibrant 2.02 Extreme accepts 0/5 default geometry fragments; `colortex4` inputs, attachment feedback, and cloud inputs remain unsupported.
- The Lavapipe shutdown still reports validation diagnostics. Physical-GPU testing, third-party-pack visual validation, and full Iris parity remain incomplete.

Unsupported active features are diagnosed instead of silently filled with invented values. See `COMPATIBILITY.md` and `VALIDATION.md` for scope and measured results.
