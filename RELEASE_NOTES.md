# Veyra 0.2.0-beta.21+mc1.21.11 — experimental prerelease

This release improves source compatibility for Iris-style cloud vertex programs. It does not claim full Iris parity, stable-release readiness, or correct rendering of complete third-party shaderpacks.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.21+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.20

- Cloud vertex helpers that are not reachable from shaderpack `main()` are pruned before compatibility checks. Reachable unsupported calls still produce an error.
- Route cloud fog fields through the existing Minecraft Fog UBO, bridge legacy `gl_ModelViewMatrix` through VulkanMod's current `ModelViewMat`, and support lowercase `texcoord` plus `dist` cloud varyings.
- Add direct shaderc fixtures and a real-client `cloud-model-view` Lavapipe readback fixture.
- PR #46 merged at `347c82086ba56716346575fa6962cd8d8398dc69`. Build [39425512471](https://github.com/glebKovshov/Veyra/actions/runs/39425512471), Graphics smoke [39425512429](https://github.com/glebKovshov/Veyra/actions/runs/39425512429) (21/21), and pinned source audit [39425512233](https://github.com/glebKovshov/Veyra/actions/runs/39425512233) passed on its final head.

## Validation

- The cloud model-view Lavapipe fixture read back 52,495 active magenta pixels, 0 disabled pixels, and 0 runtime Vulkan validation errors. Vulkan teardown diagnostics remain.
- The pinned audit covers 4 packs, 75 records, and 5,178 stages: 693 geometry fragments accepted / 24 rejected; source errors fell from 84 to 24. Every record has `visualValidation=false`.
- Forced local JDK 21 `build validateShaders packageExamplePacks compileGraphicsSmokeJava --rerun-tasks` passed 241 tests, 0 failures/errors/skips, processed 52 shaders, and compiled 49 directly to Vulkan SPIR-V.
- Release CI builds the final versioned artifact from this tag and verifies its contents and checksum manifest.

## Known limits

- Complementary Reimagined and Unbound accept 10/10 default geometry fragments in each tested dimension. Selected profiles still require `spriteBounds` and the unsupported `shadowcomp.csh` compute stage.
- BSL 10.1.8 accepts 9/9 default geometry fragments in each tested dimension. The complete pack has not been visually tested.
- Sildur's Vibrant 2.02 Extreme accepts 3/15 default geometry fragments; `colortex4` and water attachment feedback remain unsupported.
- No third-party shaderpack visual validation or physical-GPU testing has been completed. Vulkan teardown diagnostics and the broader stable-release goal remain unresolved.

Unsupported active features are diagnosed instead of silently filled with invented values. See `COMPATIBILITY.md` and `VALIDATION.md` for scope and measured results.
