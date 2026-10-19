# Veyra 0.2.0-beta.9 — experimental prerelease

This prerelease extends Veyra's supported Iris shader-pack subset on VulkanMod. It is not a stable release and does not claim full Iris parity. Active incompatibilities remain in every tested third-party pack; none is certified as playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Install `veyra-0.2.0-beta.9+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the sources JAR. VulkanMod bundles its required Fabric modules; the development build uses Fabric API 0.140.0+1.21.11.
- Optional example packs can be extracted into `shaderpacks/`. Press **F6**, select a pack, then use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.8

- Bridge terrain-family Iris `viewPos` from VulkanMod's camera-relative terrain position and the current `gbufferModelView` matrix. The shared VulkanMod terrain vertex format stays unchanged.
- Add a terrain view-position case to `geometry-view-space` and JUnit/shaderc regressions for fragment input mapping and vertex-stage compilation. An active/disabled Lavapipe readback fixture checks the terrain signal.
- Refresh the pinned source audit: BSL terrain passes source adaptation; BSL water still requires `mat`, and Complementary terrain/water still require `mc_midTexCoord`.

## Verification

- Local JDK 21 full `test validateShaders build packageExamplePacks --rerun-tasks` passed: **194 JUnit tests**, 42 shader examples processed, and 39 compiled directly to Vulkan SPIR-V. The beta.9 candidate JAR passes the distributable-content verifier.
- The pinned source-only audit covers 12 defaults and 63 declared profile/dimension cases across 5,178 preprocessed stages; **90** geometry fragments are accepted and **627** rejected. Every case has `visualValidation=false`.
- On PR #16 code head 0d12735832c1837935274a7e82d97b8af8c9bd38, Build ([run 39343008623](https://github.com/glebKovshov/Veyra/actions/runs/39343008623)), source audit ([run 39343008507](https://github.com/glebKovshov/Veyra/actions/runs/39343008507)) and all 14 Lavapipe fixtures ([run 39343008628](https://github.com/glebKovshov/Veyra/actions/runs/39343008628)) passed. The feature readback reported 222,792 active magenta pixels and 0 with shaders disabled; runtime Vulkan validation errors were 0. Process shutdown still emitted 10,031 vkDestroyDevice diagnostics, the same count and VUID category as the same-run vanilla baseline.
- Source and shaderc results do not establish third-party pack appearance.
- No physical-GPU or real-pack visual result is claimed.

## Known limits

- Complementary Reimagined and Unbound terrain/water use `signMidCoordPos`, which needs `mc_midTexCoord`; VulkanMod 0.6.8's terrain vertex writer has no midpoint-UV attribute. Selected profiles use compute stages Veyra does not execute. Entity/block/hand programs still require `currentRenderedItemId`.
- BSL water requires `mat`, entities require `entityColor`, the hand bridge lacks `isMainHand`, block programs need unsupported entity inputs, and clouds use the unsupported `texture` sampler.
- Sildur's Vibrant uses `colortex4` in non-terrain geometry, has water attachment feedback, and uses an unsupported cloud `texture` sampler.
- Normal maps, animated material maps, broader sampler/property behavior, arbitrary geometry vertex inputs, non-terrain MRT, compute/tessellation execution, physical-GPU checks, and real-pack visual validation remain incomplete.

Unsupported active features produce diagnostics instead of fake output. See [COMPATIBILITY.md](COMPATIBILITY.md), [VALIDATION.md](VALIDATION.md), and [CHANGELOG.md](CHANGELOG.md) for details and evidence.
