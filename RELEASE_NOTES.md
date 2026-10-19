# Veyra 0.2.0-beta.5 — experimental prerelease

This is an experimental supported subset of Iris shader-pack behavior on VulkanMod. It is not a stable release and does not claim full Iris parity. Stable 0.1.1 remains available. Beta.5 adds a limited cloud vertex-stage bridge; the pinned compatibility audit still finds active blockers in third-party packs, and none is certified playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Install `veyra-0.2.0-beta.5+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the sources JAR. VulkanMod bundles its required Fabric modules; the development build uses Fabric API 0.140.0+1.21.11.
- Optional example packs can be extracted into `shaderpacks/`. Press **F6**, select a pack, then use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.4

- Load `gbuffers_clouds.vsh` when a pack supplies it. The bridge supports VulkanMod's `Position` and `Color` inputs, legacy `gl_Vertex`, `gl_Color`, `gl_ModelViewProjectionMatrix`, `ftransform()`, and the `color` / `vertexDistance` outputs.
- Preserve VulkanMod's default cloud color and distance before running the pack's vertex main, allowing a pack to override only the outputs it needs.
- Reject unavailable cloud vertex attributes, texture access, unsupported outputs, and all custom cloud vertex uniforms with a diagnostic. Existing cloud fragment fog and scalar uniform support remains available.
- Add a cloud vertex example, ZIP include-loading regression, adapter tests, and a shaderc fixture that compiles the adapted vertex stage to Vulkan SPIR-V.

## Verification

- Local JDK 21 verification passes: `test --rerun-tasks validateShaders build packageExamplePacks`; 169 JUnit tests passed with zero failures/errors/skips. Shaderc processed 39 example stages, compiled 36 directly to SPIR-V, and compiled the cloud vertex fixture.
- PR #6 GitHub [Build](https://github.com/glebKovshov/Veyra/actions/runs/39305176030), [source audit](https://github.com/glebKovshov/Veyra/actions/runs/39305175917), and [graphics smoke](https://github.com/glebKovshov/Veyra/actions/runs/39305175988) passed before merge. The release workflow validates the tagged commit before publishing.
- These results cover unit, source and synthetic runtime fixtures. They do not validate complete third-party pack pipelines or visuals on a physical GPU.

## Known limits

- Cloud geometry still has no texture coordinates or texture samplers. Active cloud `colortex0` sampling remains unsupported.
- Complementary Reimagined and Unbound still require `velocity`, active cloud `colortex0`, and unsupported compute stages in selected profiles.
- BSL's source-only audit cannot resolve its registered `BIOME_DESERT` input, and its cloud fragment declares a texture uniform. Sildur's still needs `colortex4` in non-terrain geometry and defined water attachment-feedback behavior; its cloud fragment also declares a texture uniform.
- Iris' built-in legacy block ID table is not loaded when a pack has no `block.properties`, so those block-entity identifiers remain unmapped.
- Normal maps, animated material maps, broader sampler/property behavior, arbitrary geometry vertex inputs, non-terrain MRT, compute/tessellation execution, physical-GPU checks and real-pack visual validation remain incomplete.

Unsupported active features produce diagnostics instead of fake output. See [COMPATIBILITY.md](COMPATIBILITY.md), [VALIDATION.md](VALIDATION.md), and [CHANGELOG.md](CHANGELOG.md) for details and evidence.
