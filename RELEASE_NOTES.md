# Veyra 0.2.0-beta.6 — experimental prerelease

This release extends Veyra's supported Iris shader-pack subset on VulkanMod. It is not a stable release and does not claim full Iris parity. The pinned source audit still finds active blockers in all tested third-party packs; none is certified as playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Install `veyra-0.2.0-beta.6+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the sources JAR. VulkanMod bundles its required Fabric modules; the development build uses Fabric API 0.140.0+1.21.11.
- Optional example packs can be extracted into `shaderpacks/`. Press **F6**, select a pack, then use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.5

- Provide Iris' hardcoded `velocity` uniform to terrain, entity-family, and post programs. A pack definition takes precedence where Iris allows it.
- Expand Iris common, dimension, player, celestial, and hardcoded custom uniform values in supported terrain, entity-family, and post uniform blocks. Pack expressions can reference these values, including vector components.
- Match Iris defaults for eligible non-opaque shader-pack uniform declarations Iris does not register: preserve their GLSL initializer or use zero. Unsupported Iris-registered uniforms still reject the affected program.
- Match Iris custom-uniform smoothing and expression behavior for the supported functions, and keep unresolved `BIOME_*` custom definitions at zero with a warning as Iris does.
- Add regression coverage for uniform adaptation, pack overrides, expression inputs, smoother behavior, and fixed-time Nether/End handling.

## Verification

- Local JDK 21 `test --rerun-tasks validateShaders build packageExamplePacks` passes. Shader validation processes 39 example shaders, compiles 36 directly to Vulkan SPIR-V, and passes the existing cloud, entity, conditional-include, and dataflow fixtures.
- The pinned source audit covers 12 default and 63 declared-profile/dimension cases, with 5,178 preprocessed stages: 18 geometry fragment programs accepted, 699 rejected. All 75 records mark `visualValidation: false`.
- The build JAR passes the distributable-content verifier. GitHub PR and release workflow checks provide the CI evidence for this tag.
- These results cover unit, source, and synthetic runtime fixtures. They do not validate full third-party pack pipelines or visuals on a physical GPU.

## Known limits

- Cloud fragments still do not support texture coordinates, texture samplers, or active `colortex0` sampling. Complementary terrain and water still require the unsupported `lmCoord` input; selected profiles use compute stages Veyra does not execute.
- Complementary entity, block, and hand programs still require `currentRenderedItemId`. BSL still has unsupported entity color/bridge inputs and cloud texture sampling. Sildur still needs `colortex4` in non-terrain geometry and defined water attachment-feedback behavior, and its cloud fragment declares a texture sampler.
- Iris' built-in legacy block ID table is not loaded when a pack has no `block.properties`, so those block-entity identifiers remain unmapped.
- Normal maps, animated material maps, broader sampler/property behavior, arbitrary geometry vertex inputs, non-terrain MRT, compute/tessellation execution, physical-GPU checks, and real-pack visual validation remain incomplete.

Unsupported active features produce diagnostics instead of fake output. See [COMPATIBILITY.md](COMPATIBILITY.md), [VALIDATION.md](VALIDATION.md), and [CHANGELOG.md](CHANGELOG.md) for details and evidence.
