# Veyra 0.2.0-beta.2 — experimental prerelease

This is an experimental supported subset of Iris shader-pack behavior on VulkanMod. It is not a stable release and does not claim full Iris parity. Stable 0.1.1 remains available. The pinned compatibility audit still finds active blockers in all four tested third-party packs; none is certified playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Install `veyra-0.2.0-beta.2+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the sources JAR. VulkanMod bundles its required Fabric modules; the development build uses Fabric API 0.140.0+1.21.11.
- Optional example packs can be extracted into `shaderpacks/`. Press **F6**, select a pack, then use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.1

- Evaluate supported scalar `uniform.*` and `variable.*` expressions from `shaders.properties` for terrain and post programs.
- Add Iris uniforms for near/far planes, held item and block light, biome precipitation, player mood/look vector, sky color, atlas size, lightning bolt position, darkness light factor and shadow fade. Preserve a pack-defined `shadowFade` expression when present; use the Iris-compatible sun/moon calculation otherwise.
- Add `relativeEyePosition` and `eyePosition` to the supported post-uniform set. Supply Iris-unbound `entityId`, `blockEntityId`, and `currentRenderedItemId` as zero outside entity-attribute programs; actual per-draw IDs remain unsupported.
- Fix `ivec2` post-uniform blocks and only treat `entityColor` as Iris-unbound outside overlay programs.

## Verification

- Clean JDK 21 `clean test validateShaders build packageExamplePacks` passes with **159 JUnit tests**, zero failures/errors/skips; shaderc preprocesses 39 example shaders and compiles 36 directly to Vulkan SPIR-V. The JAR verifier passes.
- The pinned source-only matrix preprocesses 5,178 selected sources across 75 default/profile/dimension cases; adapters accept 18 and reject 699 geometry fragment combinations. These results are adapter checks, not complete pipeline or visual validation.
- Release publication is gated on GitHub Build, source-audit and software-Vulkan graphics workflows. The graphics fixtures are synthetic and do not certify popular packs or physical-GPU behavior.

## Known limits

- Complementary Reimagined and Unbound still require `blockEntityId`, `velocity`, and cloud `fogColor`; selected profiles also use unsupported compute stages.
- BSL still requires per-draw `entityId`/`blockEntityId`; its source-only audit also cannot resolve the registered `BIOME_DESERT` input used by `isDesert`, and its cloud fragment declares a texture uniform.
- Sildur's still needs `colortex4` in non-terrain geometry and defined water attachment feedback behavior; its cloud fragment also declares a texture uniform.
- Normal maps, animated material maps, broader sampler/property behavior, arbitrary geometry vertex inputs, non-terrain MRT, compute/tessellation execution, physical-GPU checks and real-pack visual validation remain incomplete.

Unsupported active features produce diagnostics instead of fake output. See [COMPATIBILITY.md](COMPATIBILITY.md), [VALIDATION.md](VALIDATION.md), and [CHANGELOG.md](CHANGELOG.md) for details and evidence.
