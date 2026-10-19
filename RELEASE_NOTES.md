# Veyra 0.2.0-beta.4 — experimental prerelease

This is an experimental supported subset of Iris shader-pack behavior on VulkanMod. It is not a stable release and does not claim full Iris parity. Stable 0.1.1 remains available. Beta.4 adds Iris entity and block-entity identifier mappings and per-submission Vulkan pipeline variants. The pinned compatibility audit still finds active blockers in all four tested third-party packs; none is certified playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Install `veyra-0.2.0-beta.4+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the sources JAR. VulkanMod bundles its required Fabric modules; the development build uses Fabric API 0.140.0+1.21.11.
- Optional example packs can be extracted into `shaderpacks/`. Press **F6**, select a pack, then use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.3

- Parse option-preprocessed Iris `entity.properties` and `block.properties`, including namespaced entity IDs, block state predicates, and registered block tags.
- Capture entity and block-entity identifiers per renderer submission and use cached Vulkan pipeline variants with `entityId` and `blockEntityId` constants. Unmapped identifiers use `-1`.
- Add parser, precedence, tag, option-preprocessing, shader-adapter, and shader compiler regression coverage.

## Earlier beta.2 changes

- Evaluate supported scalar `uniform.*` and `variable.*` expressions from `shaders.properties` for terrain and post programs.
- Add Iris uniforms for near/far planes, held item and block light, biome precipitation, player mood/look vector, sky color, atlas size, lightning bolt position, darkness light factor and shadow fade. Preserve a pack-defined `shadowFade` expression when present; use the Iris-compatible sun/moon calculation otherwise.
- Add `relativeEyePosition` and `eyePosition` to the supported post-uniform set. Supply Iris-unbound `entityId`, `blockEntityId`, and `currentRenderedItemId` as zero outside entity-attribute programs; actual per-draw IDs remain unsupported.
- Fix `ivec2` post-uniform blocks and only treat `entityColor` as Iris-unbound outside overlay programs.

## Verification

- JDK 21 `test --rerun-tasks validateShaders build packageExamplePacks` passes with **166 JUnit tests**, zero failures/errors/skips; shaderc preprocesses 39 example shaders and compiles 36 directly to Vulkan SPIR-V, including the entity-ID fixture.
- GitHub PR Build, the pinned source audit, and all 12 software-Vulkan Lavapipe graphics fixtures pass. The source-only audit covers 12 default and 63 declared-profile/dimension cases and accepts 18 / rejects 699 geometry fragment combinations.
- These results are adapter and synthetic runtime checks, not complete pack-pipeline or visual validation. The release workflow will run `scripts/verify_jar.py` against the published Linux JAR.

## Known limits

- Complementary Reimagined and Unbound still require `velocity`, read unsupported `colortex0` from clouds, and use unsupported compute stages in selected profiles. Their block IDs can now be supplied by `block.properties` mappings.
- Entity and block-entity IDs can now be supplied by `entity.properties` and `block.properties`. BSL's source-only audit still cannot resolve the registered `BIOME_DESERT` input used by `isDesert`, and its cloud fragment declares a texture uniform.
- Sildur's still needs `colortex4` in non-terrain geometry and defined water attachment feedback behavior; its cloud fragment also declares a texture uniform.
- Iris' built-in legacy block ID table is not loaded when a pack has no `block.properties`, so those block-entity identifiers remain unmapped.
- Normal maps, animated material maps, broader sampler/property behavior, arbitrary geometry vertex inputs, non-terrain MRT, compute/tessellation execution, physical-GPU checks and real-pack visual validation remain incomplete.

Unsupported active features produce diagnostics instead of fake output. See [COMPATIBILITY.md](COMPATIBILITY.md), [VALIDATION.md](VALIDATION.md), and [CHANGELOG.md](CHANGELOG.md) for details and evidence.
