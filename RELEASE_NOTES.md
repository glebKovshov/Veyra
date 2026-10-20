# Veyra 0.2.0-beta.10 — experimental prerelease

This prerelease extends Veyra's terrain shader interface on VulkanMod. It is not a stable release and does not claim full Iris parity. Tested third-party shaderpacks still have active source-level incompatibilities and are not certified as playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Install `veyra-0.2.0-beta.10+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the `-sources.jar`. VulkanMod bundles its required Fabric modules; the development build uses Fabric API 0.140.0+1.21.11.
- Optional example packs can be extracted into `shaderpacks/`. Press **F6**, select a pack, then use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.9

- Extend compressed terrain vertices with per-quad midpoint UV data and bridge `signMidCoordPos` using each vertex UV and its four-vertex quad midpoint.
- Bridge terrain `vertexPos`, `playerPos`, and raw `glColorRaw` inputs from VulkanMod terrain data.
- Add the `geometry-mid-texcoord` shader example, adapter regressions, direct SPIR-V coverage and a Lavapipe readback fixture that requires both midpoint sign regions.
- Refresh the pinned source audit: 126 accepted / 591 rejected geometry fragments across 75 cases / 5,178 preprocessed stages.

## Verification

- Local JDK 21.0.12.1 `test validateShaders build packageExamplePacks --rerun-tasks`: **199 tests**, 43 example stages processed and 40 direct Vulkan SPIR-V compilations. The version-bumped beta.10 JAR is rebuilt and checked by the release workflow.
- Release publication is gated on the pinned source audit and all 15 Lavapipe fixtures, followed by Gradle build/tests and distributable verification.
- The synthetic midpoint fixture validates Veyra's vertex-data bridge; it does not establish third-party pack appearance or physical-GPU compatibility.

## Known limits

- Complementary terrain now passes source adaptation, while Complementary water still needs `viewVector`; entity/block/hand programs need `currentRenderedItemId`, and selected profiles require compute execution.
- BSL water needs `mat`, entities need `entityColor`, hand needs `isMainHand`, block inputs and cloud `texture` sampling are incomplete.
- Sildur's Vibrant uses unsupported `colortex4` geometry reads, water attachment feedback, and unsupported cloud inputs.
- Normal maps, animated material maps, broader sampler/property behavior, arbitrary geometry vertex inputs, non-terrain MRT, compute/tessellation execution, physical-GPU checks, and real-pack visual validation remain incomplete.

Unsupported active features produce diagnostics instead of fake output. See [COMPATIBILITY.md](COMPATIBILITY.md), [VALIDATION.md](VALIDATION.md), and [CHANGELOG.md](CHANGELOG.md) for details and evidence.