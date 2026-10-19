# Veyra 0.2.0-beta.8 — experimental prerelease

This release extends Veyra's supported Iris shader-pack subset on VulkanMod. It is not a stable release and does not claim full Iris parity. Active incompatibilities remain in every tested third-party pack; none is certified as playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Install `veyra-0.2.0-beta.8+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the sources JAR. VulkanMod bundles its required Fabric modules; the development build uses Fabric API 0.140.0+1.21.11.
- Optional example packs can be extracted into `shaderpacks/`. Press **F6**, select a pack, then use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.7

- Map Iris terrain `lmCoord`/`lightMapCoord` to VulkanMod's existing lightmap input at location 5, and BSL terrain `color` to the existing vertex-color input at location 0.
- Name the first unsupported terrain input in adapter errors, including `mc_midTexCoord`, `viewPos`, and terrain metadata/tangent inputs.
- Preserve legacy terrain `gl_FragData` output rewriting; regression coverage protects the existing behavior.
- Refresh the pinned source audit and compatibility table. The supported alias count advances but full shaderpack fragments still reject later missing inputs.

## Verification

- Local JDK 21 `test validateShaders build packageExamplePacks` passes: 192 JUnit tests, 41 example shaders processed, and 38 compiled directly to Vulkan SPIR-V. The beta.8 candidate JAR passes the distributable-content verifier.
- The pinned audit covers 12 defaults and 63 declared profile/dimension cases across 5,178 preprocessed stages; 72 geometry fragments are accepted and 645 rejected. Every case has `visualValidation=false`.
- PR #12's Build, source audit and all 13 synthetic Lavapipe graphics fixtures passed before merge. The [beta.8 Release workflow](https://github.com/glebKovshov/Veyra/actions/runs/39338631777) then passed the final build, tests, JAR verification and publication.
- Published asset: `veyra-0.2.0-beta.8+mc1.21.11.jar`, 565,142 bytes, SHA-256 `1c6e04551a12377617fa5c714eb6daa7481a9a93dc75db0afbac2587fa7a1a85`; downloaded and checked against the release checksum manifest.
- The audit and synthetic Lavapipe fixtures do not establish third-party pack appearance. No physical-GPU or real-pack visual result is claimed.

## Known limits

- Complementary Reimagined and Unbound terrain/water use `signMidCoordPos`, which needs `mc_midTexCoord`; VulkanMod 0.6.8's terrain vertex writer has no midpoint-UV attribute. Selected profiles use compute stages Veyra does not execute. Entity/block/hand programs still require `currentRenderedItemId`.
- BSL terrain still requires `viewPos`, water requires `mat`, entities require `entityColor`, the hand bridge lacks `isMainHand`, block programs need unsupported entity inputs, and clouds use the unsupported `texture` sampler.
- Sildur's Vibrant uses `colortex4` in non-terrain geometry, has water attachment feedback, and uses an unsupported cloud `texture` sampler.
- Normal maps, animated material maps, broader sampler/property behavior, arbitrary geometry vertex inputs, non-terrain MRT, compute/tessellation execution, physical-GPU checks, and real-pack visual validation remain incomplete.

Unsupported active features produce diagnostics instead of fake output. See [COMPATIBILITY.md](COMPATIBILITY.md), [VALIDATION.md](VALIDATION.md), and [CHANGELOG.md](CHANGELOG.md) for details and evidence.
