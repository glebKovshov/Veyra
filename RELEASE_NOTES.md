# Veyra 0.2.0-beta.20+mc1.21.11 — experimental prerelease

This release adds a limited cloud-texture and vertex-input bridge for Iris-compatible shader programs. It does not claim full Iris parity, stable-release readiness, or correct rendering of complete third-party shaderpacks.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.20+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.19

- Cloud fragments can sample Minecraft's active `textures/environment/clouds.png` through the `texture` sampler. The Vulkan image reloads with Minecraft resources and is cleaned up with Veyra's cloud resources.
- Supported cloud vertex programs can request generated UVs and flat face normals. BSL `texCoord`, `normal`, `sunVec`, `upVec`, and `eastVec` are bridged through the VulkanMod cloud mesh.
- Cloud image descriptors use contiguous Vulkan bindings; the extended vertex stride is aligned for Vulkan float attributes.
- PR #43 merged at `abea76234afae81349b9b12be0aff794891284b6`. Build [39417276367](https://github.com/glebKovshov/Veyra/actions/runs/39417276367), Graphics smoke [39417276374](https://github.com/glebKovshov/Veyra/actions/runs/39417276374) (20/20), and pinned source audit [39417276455](https://github.com/glebKovshov/Veyra/actions/runs/39417276455) passed on its final head.

## Validation

- The cloud texture Lavapipe fixture read back 46,140 active magenta pixels, 0 disabled pixels, and 0 runtime Vulkan validation errors. Process teardown still emitted 10,031 VulkanMod diagnostics.
- The pinned audit covers 4 packs, 75 cases, and 5,178 stages: 690 geometry fragments accepted / 27 rejected. BSL 10.1.8 accepts 9/9 default geometry fragments in Overworld, Nether, and End. Every audit record has `visualValidation=false`.
- Forced local JDK 21 `test validateShaders build packageExamplePacks compileGraphicsSmokeJava --rerun-tasks` passed 238 tests, 0 failures/errors/skips, processed 51 shaders, and compiled 48 directly to Vulkan SPIR-V.
- Release CI builds the final versioned artifact from this tag and verifies its contents and checksum manifest.

## Known limits

- Complementary Reimagined and Unbound accept 10/10 default geometry fragments in each tested dimension. Selected profiles still require `spriteBounds` and the unsupported `shadowcomp.csh` compute stage; their cloud vertex input `lmCoord` is not provided.
- BSL 10.1.8's pinned default geometry fragments now pass source adaptation in all three dimensions. The complete pack has not been visually tested.
- Sildur's Vibrant 2.02 Extreme accepts 0/5 default geometry fragments; `colortex4`, attachment feedback, and cloud inputs remain unsupported.
- No third-party shaderpack visual validation or physical-GPU testing has been completed. Vulkan teardown diagnostics and the broader stable-release goal remain unresolved.

Unsupported active features are diagnosed instead of silently filled with invented values. See `COMPATIBILITY.md` and `VALIDATION.md` for scope and measured results.
